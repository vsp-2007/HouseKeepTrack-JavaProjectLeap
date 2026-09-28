package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateInspectionRequest;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.InspectionRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private CleaningTaskService cleaningTaskService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private InspectionService inspectionService;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("201", "SUITE", RoomStatus.CLEANED);
        room.setId(2L);
        room.setDirtyAt(LocalDateTime.now().minusHours(2));
    }

    @Test
    @DisplayName("Dual-Path Inspection: Path 1 (PASSED) transitions room to INSPECTED and records pass")
    void testInspectionPassed() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(i -> {
            Inspection ins = i.getArgument(0);
            ins.setId(10L);
            return ins;
        });

        CreateInspectionRequest req = new CreateInspectionRequest("Supervisor Alice", InspectionResult.PASSED, null);
        Inspection result = inspectionService.inspectRoom(2L, req);

        assertNotNull(result);
        assertEquals(InspectionResult.PASSED, result.getResult());
        assertEquals("Supervisor Alice", result.getSupervisorName());
        assertEquals(RoomStatus.INSPECTED, room.getStatus());
        assertEquals(InspectionResult.PASSED, room.getLastInspectionResult());

        verify(roomRepository).save(room);
        verify(cleaningTaskService, never()).createAndDispatchTask(any(), any(), any());
    }

    @Test
    @DisplayName("Dual-Path Inspection: Path 2 (FAILED) reverts room to DIRTY, generates HIGH-priority task and dispatches")
    void testInspectionFailed() {
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(i -> {
            Inspection ins = i.getArgument(0);
            ins.setId(11L);
            return ins;
        });

        CreateInspectionRequest req = new CreateInspectionRequest("Supervisor Bob", InspectionResult.FAILED, "Dust on headboard and stained mirror");
        Inspection result = inspectionService.inspectRoom(2L, req);

        assertNotNull(result);
        assertEquals(InspectionResult.FAILED, result.getResult());
        assertEquals("Dust on headboard and stained mirror", result.getFailureReason());

        // Room state reverts to DIRTY
        assertEquals(RoomStatus.DIRTY, room.getStatus());
        assertEquals(InspectionResult.FAILED, room.getLastInspectionResult());
        assertNotNull(room.getDirtyAt());

        verify(roomRepository, times(1)).save(room);
        // Generates HIGH priority task with supervisor failure reason
        verify(cleaningTaskService).createAndDispatchTask(eq(room), eq(TaskPriority.HIGH), contains("Dust on headboard"));
    }

    @Test
    @DisplayName("Inspection fails if room is not in CLEANED status (e.g. DIRTY, OCCUPIED, READY)")
    void testInspectionFailsIfNotCleaned() {
        room.setStatus(RoomStatus.DIRTY);
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));

        CreateInspectionRequest req = new CreateInspectionRequest("Supervisor Bob", InspectionResult.PASSED, null);
        InvalidRoomStateException ex = assertThrows(InvalidRoomStateException.class, () -> inspectionService.inspectRoom(2L, req));
        assertTrue(ex.getMessage().contains("Only CLEANED or INSPECTED rooms can be inspected"));
    }

    @Test
    @DisplayName("Inspection on INSPECTED room with FAILED reverts room to DIRTY and dispatches HIGH priority task")
    void testInspectionOnInspectedRoomFailed() {
        room.setStatus(RoomStatus.INSPECTED);
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(i -> {
            Inspection ins = i.getArgument(0);
            ins.setId(12L);
            return ins;
        });

        CreateInspectionRequest req = new CreateInspectionRequest("Supervisor Alice", InspectionResult.FAILED, "Stain discovered on carpet");
        Inspection result = inspectionService.inspectRoom(2L, req);

        assertNotNull(result);
        assertEquals(InspectionResult.FAILED, result.getResult());
        assertEquals(RoomStatus.DIRTY, room.getStatus());
        verify(cleaningTaskService).createAndDispatchTask(eq(room), eq(TaskPriority.HIGH), contains("Stain discovered"));
    }

    @Test
    @DisplayName("Inspection on INSPECTED room with PASSED retains INSPECTED status")
    void testInspectionOnInspectedRoomPassed() {
        room.setStatus(RoomStatus.INSPECTED);
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(i -> {
            Inspection ins = i.getArgument(0);
            ins.setId(13L);
            return ins;
        });

        CreateInspectionRequest req = new CreateInspectionRequest("Supervisor Alice", InspectionResult.PASSED, null);
        Inspection result = inspectionService.inspectRoom(2L, req);

        assertNotNull(result);
        assertEquals(InspectionResult.PASSED, result.getResult());
        assertEquals(RoomStatus.INSPECTED, room.getStatus());
        verify(cleaningTaskService, never()).createAndDispatchTask(any(), any(), any());
    }
}
