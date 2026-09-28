package com.hotel.housekeeptrack.dto;

import java.util.Map;

public class SystemSummaryDto {
    private long totalRooms;
    private Map<String, Long> roomsByStatus;
    private long totalHousekeepers;
    private Map<String, Long> housekeepersByStatus;
    private long pendingCleaningTasks;
    private long inProgressCleaningTasks;
    private long completedCleaningTasks;
    private Double avgRoomTurnaroundMinutes;
    private Double avgCleaningTaskTurnaroundMinutes;

    public SystemSummaryDto() {
    }

    public long getTotalRooms() {
        return totalRooms;
    }

    public void setTotalRooms(long totalRooms) {
        this.totalRooms = totalRooms;
    }

    public Map<String, Long> getRoomsByStatus() {
        return roomsByStatus;
    }

    public void setRoomsByStatus(Map<String, Long> roomsByStatus) {
        this.roomsByStatus = roomsByStatus;
    }

    public long getTotalHousekeepers() {
        return totalHousekeepers;
    }

    public void setTotalHousekeepers(long totalHousekeepers) {
        this.totalHousekeepers = totalHousekeepers;
    }

    public Map<String, Long> getHousekeepersByStatus() {
        return housekeepersByStatus;
    }

    public void setHousekeepersByStatus(Map<String, Long> housekeepersByStatus) {
        this.housekeepersByStatus = housekeepersByStatus;
    }

    public long getPendingCleaningTasks() {
        return pendingCleaningTasks;
    }

    public void setPendingCleaningTasks(long pendingCleaningTasks) {
        this.pendingCleaningTasks = pendingCleaningTasks;
    }

    public long getInProgressCleaningTasks() {
        return inProgressCleaningTasks;
    }

    public void setInProgressCleaningTasks(long inProgressCleaningTasks) {
        this.inProgressCleaningTasks = inProgressCleaningTasks;
    }

    public long getCompletedCleaningTasks() {
        return completedCleaningTasks;
    }

    public void setCompletedCleaningTasks(long completedCleaningTasks) {
        this.completedCleaningTasks = completedCleaningTasks;
    }

    public Double getAvgRoomTurnaroundMinutes() {
        return avgRoomTurnaroundMinutes;
    }

    public void setAvgRoomTurnaroundMinutes(Double avgRoomTurnaroundMinutes) {
        this.avgRoomTurnaroundMinutes = avgRoomTurnaroundMinutes;
    }

    public Double getAvgCleaningTaskTurnaroundMinutes() {
        return avgCleaningTaskTurnaroundMinutes;
    }

    public void setAvgCleaningTaskTurnaroundMinutes(Double avgCleaningTaskTurnaroundMinutes) {
        this.avgCleaningTaskTurnaroundMinutes = avgCleaningTaskTurnaroundMinutes;
    }
}
