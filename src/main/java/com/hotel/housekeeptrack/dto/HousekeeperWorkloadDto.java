package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.HousekeeperStatus;

public class HousekeeperWorkloadDto {
    private Long housekeeperId;
    private String housekeeperName;
    private HousekeeperStatus status;
    private Integer activeTaskCount;
    private Long completedTaskCount;
    private Double avgTurnaroundMinutes;

    public HousekeeperWorkloadDto() {
    }

    public HousekeeperWorkloadDto(Long housekeeperId, String housekeeperName, HousekeeperStatus status,
                                  Integer activeTaskCount, Long completedTaskCount, Double avgTurnaroundMinutes) {
        this.housekeeperId = housekeeperId;
        this.housekeeperName = housekeeperName;
        this.status = status;
        this.activeTaskCount = activeTaskCount;
        this.completedTaskCount = completedTaskCount;
        this.avgTurnaroundMinutes = avgTurnaroundMinutes;
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

    public HousekeeperStatus getStatus() {
        return status;
    }

    public void setStatus(HousekeeperStatus status) {
        this.status = status;
    }

    public Integer getActiveTaskCount() {
        return activeTaskCount;
    }

    public void setActiveTaskCount(Integer activeTaskCount) {
        this.activeTaskCount = activeTaskCount;
    }

    public Long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(Long completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }

    public Double getAvgTurnaroundMinutes() {
        return avgTurnaroundMinutes;
    }

    public void setAvgTurnaroundMinutes(Double avgTurnaroundMinutes) {
        this.avgTurnaroundMinutes = avgTurnaroundMinutes;
    }
}
