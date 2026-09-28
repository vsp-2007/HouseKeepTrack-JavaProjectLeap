package com.hotel.housekeeptrack.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a hotel room with its operational status and turnaround tracking.
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, unique = true)
    private String roomNumber;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status = RoomStatus.READY;

    @Column(name = "active_cleaning_task_id")
    private Long activeCleaningTaskId;

    @Column(name = "dirty_at")
    private LocalDateTime dirtyAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_inspection_result")
    private InspectionResult lastInspectionResult;

    @Version
    @Column(name = "version")
    private Long version;

    public Room() {
    }

    public Room(String roomNumber, String roomType, RoomStatus status) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.status = status != null ? status : RoomStatus.READY;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
