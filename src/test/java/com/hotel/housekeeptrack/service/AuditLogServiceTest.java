package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.model.AuditAction;
import com.hotel.housekeeptrack.model.AuditLog;
import com.hotel.housekeeptrack.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = new AuditLog(LocalDateTime.now(), AuditAction.ROOM_CREATED, "Room", 101L, "FrontDesk", "Created DELUXE room 101");
        sampleLog.setId(1L);
    }

    @Test
    @DisplayName("AuditLogService: Successfully logs audit event")
    void testLogSuccess() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> {
            AuditLog log = i.getArgument(0);
            log.setId(10L);
            return log;
        });

        AuditLog saved = auditLogService.log(AuditAction.CHECK_IN, "Room", 102L, "FrontDesk", "Guest checked in");
        assertNotNull(saved);
        assertEquals(10L, saved.getId());
        assertEquals(AuditAction.CHECK_IN, saved.getAction());
        assertEquals("Room", saved.getEntityType());
        assertEquals(102L, saved.getEntityId());
        assertEquals("FrontDesk", saved.getActor());
        assertEquals("Guest checked in", saved.getDetails());
        assertNotNull(saved.getTimestamp());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("AuditLogService: Returns paginated audit logs")
    void testGetAuditLogsPaginated() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<AuditLog> page = new PageImpl<>(Collections.singletonList(sampleLog), pageable, 1);
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<AuditLog> result = auditLogService.getAuditLogs(pageable);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(AuditAction.ROOM_CREATED, result.getContent().get(0).getAction());
        verify(auditLogRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("AuditLogService: Generates structured Markdown report with table formatting and sanitization")
    void testGenerateMarkdownReport() {
        AuditLog log1 = new AuditLog(LocalDateTime.of(2026, 9, 28, 10, 0, 0), AuditAction.ROOM_CREATED, "Room", 101L, "FrontDesk", "Created room 101 | with pipe\r\nand newline");
        log1.setId(1L);
        AuditLog log2 = new AuditLog(LocalDateTime.of(2026, 9, 28, 10, 30, 0), AuditAction.CHECKOUT, "Room", 101L, "FrontDesk", "Guest checked out");
        log2.setId(2L);

        when(auditLogRepository.findAllByOrderByTimestampAsc()).thenReturn(Arrays.asList(log1, log2));

        String markdown = auditLogService.generateMarkdownReport();
        assertNotNull(markdown);
        assertTrue(markdown.contains("# HouseKeepTrack - Operational Audit Log Report"));
        assertTrue(markdown.contains("| ID | Timestamp | Action | Entity Type | Entity ID | Actor | Details |"));
        assertTrue(markdown.contains("ROOM_CREATED"));
        assertTrue(markdown.contains("CHECKOUT"));
        assertTrue(markdown.contains("FrontDesk"));
        assertTrue(markdown.contains("Total Entries:** 2"));
        // Check escaping of pipe and removal of carriage return
        assertTrue(markdown.contains("with pipe and newline"));
        assertFalse(markdown.contains("\r"));
    }

    @Test
    @DisplayName("AuditLogService: Generates clean Plain Text report with multi-line sanitization")
    void testGeneratePlainTextReport() {
        AuditLog log1 = new AuditLog(LocalDateTime.of(2026, 9, 28, 10, 0, 0), AuditAction.STAFF_REGISTERED, "Housekeeper", 5L, "Admin", "Registered Alice\r\nWith multi-line details");
        log1.setId(1L);

        when(auditLogRepository.findAllByOrderByTimestampAsc()).thenReturn(Collections.singletonList(log1));

        String plainText = auditLogService.generatePlainTextReport();
        assertNotNull(plainText);
        assertTrue(plainText.contains("HOUSEKEEPTRACK - OPERATIONAL AUDIT TRAIL LOG REPORT"));
        assertTrue(plainText.contains("[STAFF_REGISTERED]"));
        assertTrue(plainText.contains("Entity: Housekeeper #5"));
        assertTrue(plainText.contains("Actor: Admin"));
        assertTrue(plainText.contains("Registered Alice With multi-line details"));
        assertFalse(plainText.contains("\r"));
        assertTrue(plainText.contains("Total Records: 1"));
    }

    @Test
    @DisplayName("AuditLogService: Delete audit log successfully")
    void testDeleteAuditLogSuccess() {
        when(auditLogRepository.findById(1L)).thenReturn(java.util.Optional.of(sampleLog));

        auditLogService.deleteAuditLog(1L);

        verify(auditLogRepository).delete(sampleLog);
    }

    @Test
    @DisplayName("AuditLogService: Delete audit log throws ResourceNotFoundException when not found")
    void testDeleteAuditLogNotFound() {
        when(auditLogRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThrows(com.hotel.housekeeptrack.exception.ResourceNotFoundException.class,
                () -> auditLogService.deleteAuditLog(999L));
        verify(auditLogRepository, never()).delete(any());
    }
}
