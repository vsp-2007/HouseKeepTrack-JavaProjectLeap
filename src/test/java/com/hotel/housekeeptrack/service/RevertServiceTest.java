package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.RevertActionResponse;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevertServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private CleaningTaskRepository cleaningTaskRepository;

    @Mock
    private HousekeeperRepository housekeeperRepository;

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private CleaningTaskService cleaningTaskService;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private RevertService revertService;

    private Room room;
    private Housekeeper housekeeper;

    @BeforeEach
    void setUp() {
        room = new Room("101", "DELUXE", RoomStatus.OCCUPIED);
        room.setId(10L);

        housekeeper = new Housekeeper("Bob Worker", "bob@hotel.com", "555-0100");
        housekeeper.setId(20L);
        housekeeper.setStatus(HousekeeperStatus.AVAILABLE);

        lenient().when(auditLogService.log(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            AuditAction action = invocation.getArgument(0);
            String entityType = invocation.getArgument(1);
            Long entityId = invocation.getArgument(2);
            String actor = invocation.getArgument(3);
            String details = invocation.getArgument(4);
            AuditLog log = new AuditLog(action, entityType, entityId, actor, details);
            log.setId(999L);
            return log;
        });

        Cache mockCache = mock(Cache.class);
        lenient().when(cacheManager.getCacheNames()).thenReturn(Collections.singletonList("rooms"));
        lenient().when(cacheManager.getCache("rooms")).thenReturn(mockCache);
    }

    @Test
    @DisplayName("Revert CHECK_IN: Restores room status from OCCUPIED back to READY")
    void testRevertCheckInSuccess() {
        AuditLog checkInLog = new AuditLog(LocalDateTime.now(), AuditAction.CHECK_IN, "Room", 10L, "FrontDesk", "Guest checked into room 101");
        checkInLog.setId(100L);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(checkInLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        RevertActionResponse response = revertService.revertLastAction();

        assertNotNull(response);
        assertEquals(AuditAction.CHECK_IN, response.getRevertedAction());
        assertEquals(RoomStatus.READY, room.getStatus());
        assertNotNull(room.getReadyAt());
        verify(roomRepository).save(room);
        verify(auditLogService).log(eq(AuditAction.REVERTED), eq("Room"), eq(10L), eq("Admin"), contains("AuditLog #100"));
    }

    @Test
    @DisplayName("Revert CHECKOUT: Restores room to OCCUPIED and cancels cleaning task")
    void testRevertCheckoutSuccess() {
        AuditLog checkoutLog = new AuditLog(LocalDateTime.now(), AuditAction.CHECKOUT, "Room", 10L, "FrontDesk", "Guest checked out of room 101");
        checkoutLog.setId(101L);

        room.setStatus(RoomStatus.DIRTY);
        room.setActiveCleaningTaskId(50L);

        CleaningTask task = new CleaningTask(room, TaskPriority.NORMAL, "Guest checkout cleaning");
        task.setId(50L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setHousekeeper(housekeeper);
        housekeeper.setActiveTaskCount(1);
        housekeeper.setStatus(HousekeeperStatus.BUSY);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(checkoutLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(cleaningTaskRepository.findById(50L)).thenReturn(Optional.of(task));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.CHECKOUT, response.getRevertedAction());
        assertEquals(RoomStatus.OCCUPIED, room.getStatus());
        assertNull(room.getActiveCleaningTaskId());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals(0, housekeeper.getActiveTaskCount());
        assertEquals(HousekeeperStatus.AVAILABLE, housekeeper.getStatus());
        verify(cleaningTaskRepository).save(task);
        verify(roomRepository).save(room);
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Revert MARK_READY: Restores room status back to INSPECTED")
    void testRevertMarkReadySuccess() {
        AuditLog markReadyLog = new AuditLog(LocalDateTime.now(), AuditAction.MARK_READY, "Room", 10L, "Supervisor", "Room 101 marked READY");
        markReadyLog.setId(102L);

        room.setStatus(RoomStatus.READY);
        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(markReadyLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.MARK_READY, response.getRevertedAction());
        assertEquals(RoomStatus.INSPECTED, room.getStatus());
        assertNull(room.getReadyAt());
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Revert SEND_TO_CLEANING: Restores room status to CLEANED when prior state was CLEANED")
    void testRevertSendToCleaningSuccess() {
        AuditLog sendLog = new AuditLog(LocalDateTime.now(), AuditAction.SEND_TO_CLEANING, "Room", 10L, "Supervisor", "Room 101 sent back to cleaning from CLEANED: Defect found");
        sendLog.setId(103L);

        room.setStatus(RoomStatus.DIRTY);
        room.setActiveCleaningTaskId(55L);

        CleaningTask highTask = new CleaningTask(room, TaskPriority.HIGH, "Re-clean");
        highTask.setId(55L);
        highTask.setStatus(TaskStatus.PENDING);

        Inspection failedInsp = new Inspection(room, "Supervisor", InspectionResult.FAILED, "Defect");

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(sendLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(cleaningTaskRepository.findById(55L)).thenReturn(Optional.of(highTask));
        when(inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(10L)).thenReturn(Optional.of(failedInsp));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.SEND_TO_CLEANING, response.getRevertedAction());
        assertEquals(RoomStatus.CLEANED, room.getStatus());
        assertEquals(TaskStatus.CANCELLED, highTask.getStatus());
        verify(inspectionRepository).delete(failedInsp);
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Revert SEND_TO_CLEANING: Accurately restores room to INSPECTED with PASSED inspection when prior state was INSPECTED")
    void testRevertSendToCleaningFromInspectedRestoresInspected() {
        AuditLog sendLog = new AuditLog(LocalDateTime.now(), AuditAction.SEND_TO_CLEANING, "Room", 10L, "Supervisor", "Room 101 sent back to cleaning from INSPECTED: Broken bulb");
        sendLog.setId(103L);

        room.setStatus(RoomStatus.DIRTY);
        room.setActiveCleaningTaskId(55L);

        CleaningTask highTask = new CleaningTask(room, TaskPriority.HIGH, "Re-clean");
        highTask.setId(55L);
        highTask.setStatus(TaskStatus.PENDING);

        Inspection failedInsp = new Inspection(room, "Supervisor", InspectionResult.FAILED, "Broken bulb");

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(sendLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(cleaningTaskRepository.findById(55L)).thenReturn(Optional.of(highTask));
        when(inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(10L)).thenReturn(Optional.of(failedInsp));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.SEND_TO_CLEANING, response.getRevertedAction());
        assertEquals(RoomStatus.INSPECTED, room.getStatus());
        assertEquals(InspectionResult.PASSED, room.getLastInspectionResult());
        verify(inspectionRepository).delete(failedInsp);
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Revert STAFF_STATUS_CHANGED: Restores housekeeper status to previous state and dispatches tasks")
    void testRevertStaffStatusChangedSuccess() {
        AuditLog staffLog = new AuditLog(LocalDateTime.now(), AuditAction.STAFF_STATUS_CHANGED, "Housekeeper", 20L, "Admin",
                "Status updated from AVAILABLE to OFFLINE for housekeeper Bob Worker");
        staffLog.setId(104L);

        housekeeper.setStatus(HousekeeperStatus.OFFLINE);
        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(staffLog));
        when(housekeeperRepository.existsById(20L)).thenReturn(true);
        when(housekeeperRepository.findById(20L)).thenReturn(Optional.of(housekeeper));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.STAFF_STATUS_CHANGED, response.getRevertedAction());
        assertEquals(HousekeeperStatus.AVAILABLE, housekeeper.getStatus());
        verify(housekeeperRepository).save(housekeeper);
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Revert STAFF_STATUS_CHANGED to OFFLINE re-queues active tasks to PENDING and rooms to DIRTY")
    void testRevertStaffStatusToOfflineRequeuesActiveTasks() {
        AuditLog staffLog = new AuditLog(LocalDateTime.now(), AuditAction.STAFF_STATUS_CHANGED, "Housekeeper", 20L, "Admin",
                "Status updated from OFFLINE to AVAILABLE for housekeeper Bob Worker");
        staffLog.setId(106L);

        housekeeper.setStatus(HousekeeperStatus.BUSY);
        housekeeper.setActiveTaskCount(1);

        CleaningTask activeTask = new CleaningTask(room, TaskPriority.NORMAL, "Active task");
        activeTask.setId(60L);
        activeTask.setStatus(TaskStatus.IN_PROGRESS);
        activeTask.setHousekeeper(housekeeper);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(staffLog));
        when(housekeeperRepository.existsById(20L)).thenReturn(true);
        when(housekeeperRepository.findById(20L)).thenReturn(Optional.of(housekeeper));
        when(cleaningTaskRepository.findByHousekeeperIdAndStatus(20L, TaskStatus.IN_PROGRESS))
                .thenReturn(Collections.singletonList(activeTask));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.STAFF_STATUS_CHANGED, response.getRevertedAction());
        assertEquals(HousekeeperStatus.OFFLINE, housekeeper.getStatus());
        assertEquals(0, housekeeper.getActiveTaskCount());
        assertEquals(TaskStatus.PENDING, activeTask.getStatus());
        assertNull(activeTask.getHousekeeper());
        assertEquals(RoomStatus.DIRTY, room.getStatus());
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Revert INSPECTION_PASSED: Restores room status back to CLEANED and deletes inspection")
    void testRevertInspectionPassedSuccess() {
        AuditLog passLog = new AuditLog(LocalDateTime.now(), AuditAction.INSPECTION_PASSED, "Inspection", 70L, "Supervisor", "Inspection PASSED for room 101");
        passLog.setId(105L);

        room.setStatus(RoomStatus.INSPECTED);
        Inspection insp = new Inspection(room, "Supervisor", InspectionResult.PASSED, null);
        insp.setId(70L);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(passLog));
        when(inspectionRepository.existsById(70L)).thenReturn(true);
        when(inspectionRepository.findById(70L)).thenReturn(Optional.of(insp));

        RevertActionResponse response = revertService.revertLastAction();

        assertEquals(AuditAction.INSPECTION_PASSED, response.getRevertedAction());
        assertEquals(RoomStatus.CLEANED, room.getStatus());
        assertNull(room.getLastInspectionResult());
        verify(inspectionRepository).delete(insp);
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Revert skips already-reverted actions and rolls back earlier revertible action")
    void testRevertSkipsAlreadyRevertedAction() {
        AuditLog earlierLog = new AuditLog(LocalDateTime.now().minusMinutes(10), AuditAction.CHECK_IN, "Room", 10L, "FrontDesk", "Guest checked in");
        earlierLog.setId(10L);

        AuditLog latestLog = new AuditLog(LocalDateTime.now().minusMinutes(5), AuditAction.MARK_READY, "Room", 10L, "Supervisor", "Room marked ready");
        latestLog.setId(20L);

        AuditLog alreadyRevertedLog = new AuditLog(LocalDateTime.now().minusMinutes(2), AuditAction.REVERTED, "Room", 10L, "Admin",
                "Reverted MARK_READY (AuditLog #20): Room 101 mark-ready reverted");
        alreadyRevertedLog.setId(30L);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Arrays.asList(alreadyRevertedLog, latestLog, earlierLog));
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        RevertActionResponse response = revertService.revertLastAction();

        // Must revert earlierLog (ID 10) because ID 20 was already reverted by ID 30
        assertEquals(AuditAction.CHECK_IN, response.getRevertedAction());
        assertEquals(RoomStatus.READY, room.getStatus());
    }

    @Test
    @DisplayName("Revert skips deleted room entity and reverts earlier action on surviving room")
    void testRevertSkipsDeletedRoomEntity() {
        AuditLog survivingLog = new AuditLog(LocalDateTime.now().minusMinutes(10), AuditAction.CHECK_IN, "Room", 10L, "FrontDesk", "Guest checked into room 101");
        survivingLog.setId(10L);

        AuditLog deletedRoomLog = new AuditLog(LocalDateTime.now().minusMinutes(2), AuditAction.CHECK_IN, "Room", 999L, "FrontDesk", "Guest checked into room 999");
        deletedRoomLog.setId(20L);

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Arrays.asList(deletedRoomLog, survivingLog));
        // Room 999 is deleted (existsById = false), Room 10 survives
        when(roomRepository.existsById(999L)).thenReturn(false);
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        RevertActionResponse response = revertService.revertLastAction();

        // Should skip deleted room 999 and revert Room 10
        assertEquals(AuditAction.CHECK_IN, response.getRevertedAction());
        assertEquals(10L, response.getTargetEntityId());
        assertEquals(RoomStatus.READY, room.getStatus());
    }

    @Test
    @DisplayName("Revert throws BusinessRuleException when no revertible actions exist")
    void testRevertThrowsWhenNoRevertibleActions() {
        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

        assertThrows(BusinessRuleException.class, () -> revertService.revertLastAction());
    }
}
