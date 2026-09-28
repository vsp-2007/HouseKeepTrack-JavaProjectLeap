package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.AuditLogResponse;
import com.hotel.housekeeptrack.model.AuditAction;
import com.hotel.housekeeptrack.model.AuditLog;
import com.hotel.housekeeptrack.presenter.AuditLogPresenter;
import com.hotel.housekeeptrack.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.hotel.housekeeptrack.service.RevertService revertService;

    private AuditLogPresenter auditLogPresenter;
    private AuditLogController auditLogController;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        auditLogPresenter = new AuditLogPresenter(auditLogService, revertService);
        auditLogController = new AuditLogController(auditLogPresenter);

        sampleLog = new AuditLog(LocalDateTime.now(), AuditAction.CHECKOUT, "Room", 201L, "FrontDesk", "Guest checked out");
        sampleLog.setId(10L);
    }

    @Test
    @DisplayName("AuditLogController: GET /api/audit-logs returns paginated audit log response")
    void testGetAuditLogs() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<AuditLog> logPage = new PageImpl<>(Collections.singletonList(sampleLog), pageable, 1);
        when(auditLogService.getAuditLogs(any(Pageable.class))).thenReturn(logPage);

        ResponseEntity<Page<AuditLogResponse>> response = auditLogController.getAuditLogs(pageable);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(AuditAction.CHECKOUT, response.getBody().getContent().get(0).getAction());
        assertEquals("Room", response.getBody().getContent().get(0).getEntityType());
        assertEquals(201L, response.getBody().getContent().get(0).getEntityId());
    }

    @Test
    @DisplayName("AuditLogController: GET /api/audit-logs/download?format=md returns markdown attachment")
    void testDownloadAuditLogsMarkdown() {
        String mockReport = "# HouseKeepTrack - Operational Audit Log Report\n| ID | Timestamp | Action |";
        when(auditLogService.generateMarkdownReport()).thenReturn(mockReport);

        ResponseEntity<byte[]> response = auditLogController.downloadAuditLogs("md");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("attachment; filename=\"housekeeptrack-audit-log-"));
        assertTrue(contentDisposition.endsWith(".md\""));

        assertTrue(response.getHeaders().getContentType().toString().contains("text/markdown"));
        String bodyText = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(bodyText.contains("# HouseKeepTrack - Operational Audit Log Report"));
    }

    @Test
    @DisplayName("AuditLogController: GET /api/audit-logs/download?format=txt returns plain text attachment")
    void testDownloadAuditLogsPlainText() {
        String mockReport = "================================================================================\nHOUSEKEEPTRACK - OPERATIONAL AUDIT TRAIL LOG REPORT";
        when(auditLogService.generatePlainTextReport()).thenReturn(mockReport);

        ResponseEntity<byte[]> response = auditLogController.downloadAuditLogs("txt");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("attachment; filename=\"housekeeptrack-audit-log-"));
        assertTrue(contentDisposition.endsWith(".txt\""));

        assertTrue(response.getHeaders().getContentType().toString().contains("text/plain"));
        String bodyText = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(bodyText.contains("HOUSEKEEPTRACK - OPERATIONAL AUDIT TRAIL LOG REPORT"));
    }

    @Test
    @DisplayName("AuditLogController: GET /api/audit-logs/download?format=invalid throws IllegalArgumentException")
    void testDownloadAuditLogsUnsupportedFormatThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> auditLogController.downloadAuditLogs("unsupported_format"));
    }

    @Test
    @DisplayName("AuditLogController: POST /api/audit-logs/revert-last successfully reverts action")
    void testRevertLastAction() {
        com.hotel.housekeeptrack.dto.RevertActionResponse mockResponse =
                new com.hotel.housekeeptrack.dto.RevertActionResponse("Reverted CHECK_IN", AuditAction.CHECK_IN, 101L, "Room", null);
        when(revertService.revertLastAction()).thenReturn(mockResponse);

        ResponseEntity<com.hotel.housekeeptrack.dto.RevertActionResponse> response = auditLogController.revertLastAction();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(AuditAction.CHECK_IN, response.getBody().getRevertedAction());
        assertEquals("Reverted CHECK_IN", response.getBody().getMessage());
    }

    @Test
    @DisplayName("AuditLogController: DELETE /api/audit-logs/{id} returns no content")
    void testDeleteAuditLog() {
        doNothing().when(auditLogService).deleteAuditLog(10L);

        ResponseEntity<Void> response = auditLogController.deleteAuditLog(10L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(auditLogService).deleteAuditLog(10L);
    }
}
