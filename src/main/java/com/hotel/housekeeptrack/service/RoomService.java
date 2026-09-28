package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
import com.hotel.housekeeptrack.model.Inspection;
import com.hotel.housekeeptrack.model.InspectionResult;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.model.TaskPriority;
import com.hotel.housekeeptrack.repository.InspectionRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
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

    public RoomService(RoomRepository roomRepository,
                       CleaningTaskService cleaningTaskService,
                       InspectionRepository inspectionRepository) {
        this.roomRepository = roomRepository;
        this.cleaningTaskService = cleaningTaskService;
        this.inspectionRepository = inspectionRepository;
    }

    @Transactional
    public Room createRoom(CreateRoomRequest request) {
        if (roomRepository.existsByRoomNumber(request.getRoomNumber())) {
            throw new BusinessRuleException("Room number " + request.getRoomNumber() + " already exists.");
        }
        Room room = new Room(request.getRoomNumber(), request.getRoomType(), RoomStatus.READY);
        room.setReadyAt(LocalDateTime.now());
        return roomRepository.save(room);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<Room> getRoomsByStatus(RoomStatus status) {
        return roomRepository.findByStatus(status);
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

        return roomRepository.findById(roomId).orElse(room);
    }

    /**
     * Guest Check-In (Allocation Barrier):
     * Strictly enforces that a room can ONLY be allocated to a guest if status is READY.
     * Prevents hotel guests from checking into dirty, uncleaned, or uninspected rooms.
     */
    @Transactional
    public Room checkIn(Long roomId) {
        Room room = getRoomById(roomId);

        if (room.getStatus() != RoomStatus.READY) {
            throw new InvalidRoomStateException("Cannot check-in to Room " + room.getRoomNumber() +
                    ": room is currently " + room.getStatus() + ". Room cannot be allocated to a guest unless its status is strictly READY.");
        }

        room.setStatus(RoomStatus.OCCUPIED);
        return roomRepository.save(room);
    }

    /**
     * Mark Ready:
     * Room cannot transition to READY unless it is in the INSPECTED stage AND passed inspection.
     */
    @Transactional
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
        return roomRepository.save(room);
    }

    /**
     * Sends a room back to cleaning (from CLEANED or INSPECTED) due to improper work or failed check.
     * Transitions room back to DIRTY, records failed inspection, auto-generates a HIGH-priority re-cleaning task,
     * and triggers queue dispatch.
     */
    @Transactional
    public Room sendBackToCleaning(Long roomId, String reason, String supervisorName) {
        Room room = getRoomById(roomId);

        // State Machine Guard: Can only send back to cleaning if CLEANED or INSPECTED
        if (room.getStatus() != RoomStatus.CLEANED && room.getStatus() != RoomStatus.INSPECTED) {
            throw new InvalidRoomStateException("Cannot send Room " + room.getRoomNumber() +
                    " back to cleaning: room is currently " + room.getStatus() + ". Only CLEANED or INSPECTED rooms can be sent back to cleaning.");
        }

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

        return roomRepository.findById(roomId).orElse(room);
    }
}
