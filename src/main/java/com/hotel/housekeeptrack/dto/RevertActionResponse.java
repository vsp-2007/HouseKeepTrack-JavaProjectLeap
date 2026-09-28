package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.AuditAction;

/**
 * DTO representing the outcome of a single-action revert operation.
 */
public class RevertActionResponse {

    private String message;
    private AuditAction revertedAction;
    private Long targetEntityId;
    private String targetEntityType;
    private AuditLogResponse auditLog;

    public RevertActionResponse() {
    }

    public RevertActionResponse(String message, AuditAction revertedAction, Long targetEntityId, String targetEntityType, AuditLogResponse auditLog) {
        this.message = message;
        this.revertedAction = revertedAction;
        this.targetEntityId = targetEntityId;
        this.targetEntityType = targetEntityType;
        this.auditLog = auditLog;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AuditAction getRevertedAction() {
        return revertedAction;
    }

    public void setRevertedAction(AuditAction revertedAction) {
        this.revertedAction = revertedAction;
    }

    public Long getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(Long targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public String getTargetEntityType() {
        return targetEntityType;
    }

    public void setTargetEntityType(String targetEntityType) {
        this.targetEntityType = targetEntityType;
    }

    public AuditLogResponse getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(AuditLogResponse auditLog) {
        this.auditLog = auditLog;
    }
}
