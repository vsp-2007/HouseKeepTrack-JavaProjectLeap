package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.CleaningTask;
import com.hotel.housekeeptrack.model.TaskPriority;
import com.hotel.housekeeptrack.model.TaskStatus;

import java.time.LocalDateTime;

public class CleaningTaskResponse {
    private Long id;
    private Long roomId;
    private String roomNumber;
    private Long housekeeperId;
    private String housekeeperName;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String notes;

    public CleaningTaskResponse() {
    }

    public static CleaningTaskResponse fromEntity(CleaningTask task) {
        CleaningTaskResponse response = new CleaningTaskResponse();
        response.setId(task.getId());
        if (task.getRoom() != null) {
            response.setRoomId(task.getRoom().getId());
            response.setRoomNumber(task.getRoom().getRoomNumber());
        }
        if (task.getHousekeeper() != null) {
            response.setHousekeeperId(task.getHousekeeper().getId());
            response.setHousekeeperName(task.getHousekeeper().getName());
        }
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setCreatedAt(task.getCreatedAt());
        response.setAssignedAt(task.getAssignedAt());
        response.setStartedAt(task.getStartedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setNotes(task.getNotes());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public Long getHousekeeperId() {
        return housekeeperId;
    }

    public void setHousekeeperId(Long housekeeperId) {
        this.housekeeperId = housekeeperId;
    }

    public String getHousekeeperName() {
        return housekeeperName;
    }

    public void setHousekeeperName(String housekeeperName) {
        this.housekeeperName = housekeeperName;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
