package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
import com.hotel.housekeeptrack.model.AuditAction;
import com.hotel.housekeeptrack.model.AuditLog;
import com.hotel.housekeeptrack.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service managing audit logging, pagination, and multi-format report exports (Markdown & Plain Text).
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an operational audit event.
     */
    @Transactional
    public AuditLog log(AuditAction action, String entityType, Long entityId, String actor, String details) {
        AuditLog auditLog = new AuditLog(action, entityType, entityId, actor, details);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Retrieves paginated audit logs ordered chronologically descending by default,
     * while supporting dynamic sorting requested via Pageable.
     */
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        if (pageable == null) {
            pageable = org.springframework.data.domain.PageRequest.of(0, 5, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));
        } else if (pageable.getSort().isUnsorted()) {
            pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));
        }
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Retrieves all audit logs ordered chronologically ascending for export.
     */
    public List<AuditLog> getAllAuditLogsAsc() {
        return auditLogRepository.findAllByOrderByTimestampAsc();
    }

    /**
     * Generates a structured Markdown (.md) formatted export of all audit logs.
     */
    public String generateMarkdownReport() {
        List<AuditLog> logs = getAllAuditLogsAsc();
        StringBuilder sb = new StringBuilder();

        sb.append("# HouseKeepTrack - Operational Audit Log Report\n\n");
        sb.append("**Export Date:** ").append(LocalDateTime.now().format(ISO_FORMATTER)).append("  \n");
        sb.append("**Total Entries:** ").append(logs.size()).append("\n\n");
        sb.append("| ID | Timestamp | Action | Entity Type | Entity ID | Actor | Details |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|\n");

        for (AuditLog log : logs) {
            String ts = (log.getTimestamp() != null) ? log.getTimestamp().format(ISO_FORMATTER) : "-";
            String action = (log.getAction() != null) ? log.getAction().name() : "-";
            String entityType = (log.getEntityType() != null) ? log.getEntityType() : "-";
            String entityId = (log.getEntityId() != null) ? String.valueOf(log.getEntityId()) : "-";
            String actor = escapeMarkdown(log.getActor());
            String details = escapeMarkdown(log.getDetails());

            sb.append("| ").append(log.getId())
              .append(" | ").append(ts)
              .append(" | ").append(action)
              .append(" | ").append(entityType)
              .append(" | ").append(entityId)
              .append(" | ").append(actor)
              .append(" | ").append(details)
              .append(" |\n");
        }

        return sb.toString();
    }

    /**
     * Generates a clean Plain Text (.txt) formatted export of all audit logs.
     */
    public String generatePlainTextReport() {
        List<AuditLog> logs = getAllAuditLogsAsc();
        StringBuilder sb = new StringBuilder();

        sb.append("================================================================================\n");
        sb.append("HOUSEKEEPTRACK - OPERATIONAL AUDIT TRAIL LOG REPORT\n");
        sb.append("Export Date: ").append(LocalDateTime.now().format(ISO_FORMATTER)).append("\n");
        sb.append("Total Records: ").append(logs.size()).append("\n");
        sb.append("================================================================================\n\n");

        for (AuditLog log : logs) {
            String ts = (log.getTimestamp() != null) ? log.getTimestamp().format(ISO_FORMATTER) : "N/A";
            String action = (log.getAction() != null) ? log.getAction().name() : "UNKNOWN";
            String entityType = (log.getEntityType() != null) ? log.getEntityType() : "-";
            String entityId = (log.getEntityId() != null) ? String.valueOf(log.getEntityId()) : "-";
            String actor = (log.getActor() != null) ? log.getActor() : "Unknown";
            String details = (log.getDetails() != null) ? log.getDetails().replace("\r", "").replace("\n", " ") : "-";

            sb.append(String.format("[%s] [%s] Entity: %s #%s | Actor: %s | Details: %s\n",
                    ts, action, entityType, entityId, actor, details));
        }

        sb.append("\n================================================================================\n");
        sb.append("END OF AUDIT TRAIL REPORT\n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    private String escapeMarkdown(String value) {
        if (value == null) return "-";
        return value.replace("|", "\\|").replace("\r", "").replace("\n", " ");
    }

    /**
     * Deletes a specific audit log record by ID.
     */
    @Transactional
    public void deleteAuditLog(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog not found with ID: " + id));
        auditLogRepository.delete(auditLog);
    }
}
