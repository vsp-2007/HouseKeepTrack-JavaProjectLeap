package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.Inspection;
import com.hotel.housekeeptrack.model.InspectionResult;

import java.time.LocalDateTime;

public class InspectionResponse {
    private Long id;
    private Long roomId;
    private String roomNumber;
    private String supervisorName;
    private InspectionResult result;
    private String failureReason;
    private LocalDateTime inspectedAt;

    public InspectionResponse() {
    }

    public static InspectionResponse fromEntity(Inspection inspection) {
        InspectionResponse response = new InspectionResponse();
        response.setId(inspection.getId());
        if (inspection.getRoom() != null) {
            response.setRoomId(inspection.getRoom().getId());
            response.setRoomNumber(inspection.getRoom().getRoomNumber());
        }
        response.setSupervisorName(inspection.getSupervisorName());
        response.setResult(inspection.getResult());
        response.setFailureReason(inspection.getFailureReason());
        response.setInspectedAt(inspection.getInspectedAt());
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

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public InspectionResult getResult() {
        return result;
    }

    public void setResult(InspectionResult result) {
        this.result = result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(LocalDateTime inspectedAt) {
        this.inspectedAt = inspectedAt;
    }
}
