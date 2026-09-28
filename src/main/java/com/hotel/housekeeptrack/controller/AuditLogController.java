package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.AuditLogResponse;
import com.hotel.housekeeptrack.dto.RevertActionResponse;
import com.hotel.housekeeptrack.presenter.AuditLogPresenter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Spring MVC REST Controller for operational audit logging, reverts, and download exports.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogPresenter auditLogPresenter;

    public AuditLogController(AuditLogPresenter auditLogPresenter) {
        this.auditLogPresenter = auditLogPresenter;
    }

    /**
     * Retrieves paginated audit logs (default page size: 5, sorted descending by timestamp).
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @PageableDefault(size = 5, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditLogPresenter.presentAuditLogs(pageable);
    }

    /**
     * Reverts the most recent revertible action in the audit log.
     */
    @PostMapping("/revert-last")
    public ResponseEntity<RevertActionResponse> revertLastAction() {
        return auditLogPresenter.presentRevertLastAction();
    }

    /**
     * Deletes a specific audit log record.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuditLog(@PathVariable Long id) {
        return auditLogPresenter.presentDeletedAuditLog(id);
    }

    /**
     * Downloads complete audit logs in Markdown (.md) or Plain Text (.txt) format.
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadAuditLogs(@RequestParam(defaultValue = "md") String format) {
        return auditLogPresenter.presentDownload(format);
    }
}
