package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.AuditAction;
import com.hotel.housekeeptrack.model.AuditLog;

import java.time.LocalDateTime;

/**
 * DTO representing an audit log entry response in REST view models.
 */
public class AuditLogResponse {

    private Long id;
    private LocalDateTime timestamp;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private String actor;
    private String details;

    public AuditLogResponse() {
    }

    public static AuditLogResponse fromEntity(AuditLog log) {
        AuditLogResponse res = new AuditLogResponse();
        res.setId(log.getId());
        res.setTimestamp(log.getTimestamp());
        res.setAction(log.getAction());
        res.setEntityType(log.getEntityType());
        res.setEntityId(log.getEntityId());
        res.setActor(log.getActor());
        res.setDetails(log.getDetails());
        return res;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
