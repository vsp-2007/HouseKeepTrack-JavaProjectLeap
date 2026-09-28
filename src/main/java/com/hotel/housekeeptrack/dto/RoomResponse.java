package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.InspectionResult;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;

import java.time.LocalDateTime;

public class RoomResponse {
    private Long id;
    private String roomNumber;
    private String roomType;
    private RoomStatus status;
    private Long activeCleaningTaskId;
    private LocalDateTime dirtyAt;
    private LocalDateTime readyAt;
    private InspectionResult lastInspectionResult;

    public RoomResponse() {
    }

    public static RoomResponse fromEntity(Room room) {
        RoomResponse response = new RoomResponse();
        response.setId(room.getId());
        response.setRoomNumber(room.getRoomNumber());
        response.setRoomType(room.getRoomType());
        response.setStatus(room.getStatus());
        response.setActiveCleaningTaskId(room.getActiveCleaningTaskId());
        response.setDirtyAt(room.getDirtyAt());
        response.setReadyAt(room.getReadyAt());
        response.setLastInspectionResult(room.getLastInspectionResult());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Long getActiveCleaningTaskId() {
        return activeCleaningTaskId;
    }

    public void setActiveCleaningTaskId(Long activeCleaningTaskId) {
        this.activeCleaningTaskId = activeCleaningTaskId;
    }

    public LocalDateTime getDirtyAt() {
        return dirtyAt;
    }

    public void setDirtyAt(LocalDateTime dirtyAt) {
        this.dirtyAt = dirtyAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public InspectionResult getLastInspectionResult() {
        return lastInspectionResult;
    }

    public void setLastInspectionResult(InspectionResult lastInspectionResult) {
        this.lastInspectionResult = lastInspectionResult;
    }
}
