package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.InspectionRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing room operations, state transitions, checkout, check-in barrier, and mark-ready flow.
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final CleaningTaskService cleaningTaskService;
    private final InspectionRepository inspectionRepository;
    private final CleaningTaskRepository cleaningTaskRepository;
    private final HousekeeperRepository housekeeperRepository;
    private final AuditLogService auditLogService;

    public RoomService(RoomRepository roomRepository,
                       CleaningTaskService cleaningTaskService,
                       InspectionRepository inspectionRepository,
                       CleaningTaskRepository cleaningTaskRepository,
                       HousekeeperRepository housekeeperRepository,
                       AuditLogService auditLogService) {
        this.roomRepository = roomRepository;
        this.cleaningTaskService = cleaningTaskService;
        this.inspectionRepository = inspectionRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.housekeeperRepository = housekeeperRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary"}, allEntries = true)
    public Room createRoom(CreateRoomRequest request) {
        if (roomRepository.existsByRoomNumber(request.getRoomNumber())) {
            throw new BusinessRuleException("Room number " + request.getRoomNumber() + " already exists.");
        }
        Room room = new Room(request.getRoomNumber(), request.getRoomType(), RoomStatus.READY);
        room.setReadyAt(LocalDateTime.now());
        Room savedRoom = roomRepository.save(room);

        auditLogService.log(AuditAction.ROOM_CREATED, "Room", savedRoom.getId(), "FrontDesk",
                "Created " + savedRoom.getRoomType() + " room " + savedRoom.getRoomNumber());

        return savedRoom;
    }

    @Cacheable(value = "rooms")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @Cacheable(value = "rooms", key = "#status")
    public List<Room> getRoomsByStatus(RoomStatus status) {
        return roomRepository.findByStatus(status);
    }

    public Page<Room> getRoomsPaginated(RoomStatus status, Pageable pageable) {
        if (status != null) {
            return roomRepository.findByStatus(status, pageable);
        }
        return roomRepository.findAll(pageable);
    }

    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));
    }

    /**
     * Guest Checkout: Transitions room from OCCUPIED to DIRTY.
     * Auto-generates a NORMAL priority CleaningTask and triggers queue assignment.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "roomTurnaround", "housekeeperWorkloads"}, allEntries = true)
    public Room checkOut(Long roomId) {
        Room room = getRoomById(roomId);

        // State Machine Guard: Checkout only allowed from OCCUPIED
        if (room.getStatus() != RoomStatus.OCCUPIED) {
            throw new InvalidRoomStateException("Cannot checkout Room " + room.getRoomNumber() +
                    ": room is currently " + room.getStatus() + ". Room must be OCCUPIED to checkout.");
        }

        room.setStatus(RoomStatus.DIRTY);
        LocalDateTime now = LocalDateTime.now();
        room.setDirtyAt(now);
        room.setReadyAt(null);
        room.setLastInspectionResult(null);
        room = roomRepository.save(room);

        // Automatically create cleaning task and dispatch
        cleaningTaskService.createAndDispatchTask(room, TaskPriority.NORMAL, "Guest checkout cleaning");

        auditLogService.log(AuditAction.CHECKOUT, "Room", room.getId(), "FrontDesk",
                "Guest checked out of room " + room.getRoomNumber());

        return roomRepository.findById(roomId).orElse(room);
    }

    /**
     * Guest Check-In (Allocation Barrier):
     * Strictly enforces that a room can ONLY be allocated to a guest if status is READY.
     * Prevents hotel guests from checking into dirty, uncleaned, or uninspected rooms.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary"}, allEntries = true)
    public Room checkIn(Long roomId) {
        Room room = getRoomById(roomId);

        if (room.getStatus() != RoomStatus.READY) {
            throw new InvalidRoomStateException("Cannot check-in to Room " + room.getRoomNumber() +
                    ": room is currently " + room.getStatus() + ". Room cannot be allocated to a guest unless its status is strictly READY.");
        }

        room.setStatus(RoomStatus.OCCUPIED);
        Room savedRoom = roomRepository.save(room);

        auditLogService.log(AuditAction.CHECK_IN, "Room", savedRoom.getId(), "FrontDesk",
                "Guest checked into room " + savedRoom.getRoomNumber());

        return savedRoom;
    }

    /**
     * Mark Ready:
     * Room cannot transition to READY unless it is in the INSPECTED stage AND passed inspection.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "roomTurnaround"}, allEntries = true)
    public Room markReady(Long roomId) {
        Room room = getRoomById(roomId);

        if (room.getStatus() != RoomStatus.INSPECTED) {
            throw new InvalidRoomStateException("Cannot mark Room " + room.getRoomNumber() +
                    " as READY: room is currently " + room.getStatus() + ". Room must be in INSPECTED status before transitioning to READY.");
        }

        if (room.getLastInspectionResult() != InspectionResult.PASSED) {
            throw new BusinessRuleException("Room " + room.getRoomNumber() +
                    " cannot transition to READY because it has not passed inspection.");
        }

        room.setStatus(RoomStatus.READY);
        room.setReadyAt(LocalDateTime.now());
        Room savedRoom = roomRepository.save(room);

        auditLogService.log(AuditAction.MARK_READY, "Room", savedRoom.getId(), "Supervisor",
                "Room " + savedRoom.getRoomNumber() + " marked READY after passing inspection");

        return savedRoom;
    }

    /**
     * Sends a room back to cleaning (from CLEANED or INSPECTED) due to improper work or failed check.
     * Transitions room back to DIRTY, records failed inspection, auto-generates a HIGH-priority re-cleaning task,
     * and triggers queue dispatch.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "roomTurnaround", "housekeeperWorkloads"}, allEntries = true)
    public Room sendBackToCleaning(Long roomId, String reason, String supervisorName) {
        Room room = getRoomById(roomId);

        // State Machine Guard: Can only send back to cleaning if CLEANED or INSPECTED
        if (room.getStatus() != RoomStatus.CLEANED && room.getStatus() != RoomStatus.INSPECTED) {
            throw new InvalidRoomStateException("Cannot send Room " + room.getRoomNumber() +
                    " back to cleaning: room is currently " + room.getStatus() + ". Only CLEANED or INSPECTED rooms can be sent back to cleaning.");
        }

        RoomStatus previousStatus = room.getStatus();
        room.setStatus(RoomStatus.DIRTY);
        room.setDirtyAt(LocalDateTime.now());
        room.setReadyAt(null);
        room.setLastInspectionResult(InspectionResult.FAILED);
        room = roomRepository.save(room);

        String failureReason = (reason != null && !reason.isBlank()) ? reason : "Improper cleaning or defect identified";
        String supervisor = (supervisorName != null && !supervisorName.isBlank()) ? supervisorName : "Supervisor";

        // Record inspection failure
        Inspection inspection = new Inspection(room, supervisor, InspectionResult.FAILED, failureReason);
        inspectionRepository.save(inspection);

        // Auto-generate HIGH-priority re-cleaning task and dispatch
        String notes = "Re-cleaning Required: " + failureReason + " (Reported by " + supervisor + ")";
        cleaningTaskService.createAndDispatchTask(room, TaskPriority.HIGH, notes);

        auditLogService.log(AuditAction.SEND_TO_CLEANING, "Room", room.getId(), supervisor,
                "Room " + room.getRoomNumber() + " sent back to cleaning from " + previousStatus + ": " + failureReason);

        return roomRepository.findById(roomId).orElse(room);
    }

    /**
     * Safely deletes a room:
     * Cleans up associated cleaning tasks (freeing assigned housekeepers),
     * deletes inspections, deletes the room entity, records deletion in AuditLog,
     * and evicts caches.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "roomTurnaround", "housekeeperWorkloads"}, allEntries = true)
    public void deleteRoom(Long roomId) {
        Room room = getRoomById(roomId);

        // 1. Clean up associated cleaning tasks and free assigned housekeepers
        List<CleaningTask> tasks = cleaningTaskRepository.findByRoomId(roomId);
        for (CleaningTask task : tasks) {
            if (task.getHousekeeper() != null && task.getStatus() == TaskStatus.IN_PROGRESS) {
                Housekeeper hk = task.getHousekeeper();
                hk.setActiveTaskCount(Math.max(0, hk.getActiveTaskCount() - 1));
                if (hk.getActiveTaskCount() == 0 && hk.getStatus() == HousekeeperStatus.BUSY) {
                    hk.setStatus(HousekeeperStatus.AVAILABLE);
                }
                housekeeperRepository.save(hk);
            }
        }
        cleaningTaskRepository.deleteAll(tasks);

        // 2. Clean up associated inspections
        List<Inspection> inspections = inspectionRepository.findByRoomIdOrderByInspectedAtDesc(roomId);
        inspectionRepository.deleteAll(inspections);

        // 3. Delete room entity
        roomRepository.delete(room);

        // 4. Record deletion in AuditLog
        auditLogService.log(AuditAction.ROOM_DELETED, "Room", roomId, "Admin",
                "Deleted room " + room.getRoomNumber() + " (" + room.getRoomType() + ")");

        // 5. Trigger dispatch if housekeepers became available
        cleaningTaskService.dispatchToAvailableHousekeepers();
    }
}
