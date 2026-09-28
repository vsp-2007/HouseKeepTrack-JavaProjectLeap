package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.AuditLogResponse;
import com.hotel.housekeeptrack.model.AuditLog;
import com.hotel.housekeeptrack.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Presenter mediating between AuditLog domain model and REST view models.
 */
@Component
public class AuditLogPresenter {

    private final AuditLogService auditLogService;
    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public AuditLogPresenter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public ResponseEntity<Page<AuditLogResponse>> presentAuditLogs(Pageable pageable) {
        Page<AuditLog> page = auditLogService.getAuditLogs(pageable);
        Page<AuditLogResponse> responsePage = page.map(AuditLogResponse::fromEntity);
        return ResponseEntity.ok(responsePage);
    }

    public ResponseEntity<byte[]> presentDownload(String format) {
        if (format == null || format.isBlank()) {
            format = "md";
        }
        boolean isMarkdown = format.equalsIgnoreCase("md") || format.equalsIgnoreCase("markdown");
        boolean isPlainText = format.equalsIgnoreCase("txt") || format.equalsIgnoreCase("text");
        if (!isMarkdown && !isPlainText) {
            throw new IllegalArgumentException("Unsupported download format: '" + format + "'. Supported formats are 'md' and 'txt'.");
        }

        String extension = isMarkdown ? "md" : "txt";
        String content = isMarkdown ? auditLogService.generateMarkdownReport() : auditLogService.generatePlainTextReport();
        String timestamp = LocalDateTime.now().format(FILE_TS_FORMATTER);
        String filename = "housekeeptrack-audit-log-" + timestamp + "." + extension;

        MediaType mediaType = isMarkdown
                ? MediaType.parseMediaType("text/markdown; charset=UTF-8")
                : MediaType.parseMediaType("text/plain; charset=UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(mediaType)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
