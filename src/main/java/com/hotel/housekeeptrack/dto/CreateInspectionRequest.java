package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.InspectionResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateInspectionRequest {

    @NotBlank(message = "Supervisor name is required")
    private String supervisorName;

    @NotNull(message = "Inspection result is required (PASSED or FAILED)")
    private InspectionResult result;

    private String failureReason;

    public CreateInspectionRequest() {
    }

    public CreateInspectionRequest(String supervisorName, InspectionResult result, String failureReason) {
        this.supervisorName = supervisorName;
        this.result = result;
        this.failureReason = failureReason;
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
}
