package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateInspectionRequest;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.InspectionRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing room inspections, dual-path outcome processing,
 * and high-priority re-cleaning loops upon failure.
 */
@Service
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final RoomRepository roomRepository;
    private final CleaningTaskService cleaningTaskService;

    public InspectionService(InspectionRepository inspectionRepository,
                             RoomRepository roomRepository,
                             CleaningTaskService cleaningTaskService) {
        this.inspectionRepository = inspectionRepository;
        this.roomRepository = roomRepository;
        this.cleaningTaskService = cleaningTaskService;
    }

    /**
     * Inspects a CLEANED room.
     * Path 1 (PASSED): Room transitions to INSPECTED, becoming eligible for READY status.
     * Path 2 (FAILED): Room status reverts to DIRTY, auto-generates a HIGH-priority re-cleaning task,
     *                  and immediately triggers priority dispatch.
     */
    @Transactional
    public Inspection inspectRoom(Long roomId, CreateInspectionRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        // State Machine Guard: Only CLEANED or INSPECTED rooms may be inspected
        if (room.getStatus() != RoomStatus.CLEANED && room.getStatus() != RoomStatus.INSPECTED) {
            throw new InvalidRoomStateException("Cannot inspect Room " + room.getRoomNumber() +
                    ": room is currently " + room.getStatus() + ". Only CLEANED or INSPECTED rooms can be inspected.");
        }

        Inspection inspection = new Inspection(
                room,
                request.getSupervisorName(),
                request.getResult(),
                request.getFailureReason()
        );
        inspection = inspectionRepository.save(inspection);

        room.setLastInspectionResult(request.getResult());

        if (request.getResult() == InspectionResult.PASSED) {
            // Path 1: Pass inspection -> Room enters INSPECTED status (eligible for READY)
            room.setStatus(RoomStatus.INSPECTED);
            roomRepository.save(room);
        } else {
            // Path 2: Fail inspection -> Room reverts to DIRTY, dirtyAt resets, HIGH-priority re-cleaning task created
            room.setStatus(RoomStatus.DIRTY);
            room.setDirtyAt(LocalDateTime.now());
            roomRepository.save(room);

            String notes = "Inspection Failed: " + (request.getFailureReason() != null ? request.getFailureReason() : "Improper cleaning reported by " + request.getSupervisorName());
            cleaningTaskService.createAndDispatchTask(room, TaskPriority.HIGH, notes);
        }

        return inspection;
    }

    public List<Inspection> getInspectionsForRoom(Long roomId) {
        return inspectionRepository.findByRoomIdOrderByInspectedAtDesc(roomId);
    }

    public List<Inspection> getAllInspections() {
        return inspectionRepository.findAll();
    }
}
