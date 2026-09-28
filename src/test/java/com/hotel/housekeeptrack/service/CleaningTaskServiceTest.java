package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleaningTaskServiceTest {

    @Mock
    private CleaningTaskRepository cleaningTaskRepository;

    @Mock
    private HousekeeperRepository housekeeperRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CleaningTaskService cleaningTaskService;

    private Housekeeper housekeeper;
    private Room room1;
    private Room room2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleaningTaskService, "escalationThresholdMinutes", 15L);

        housekeeper = new Housekeeper("John Doe", "john@example.com", "1234567890");
        housekeeper.setId(10L);
        housekeeper.setStatus(HousekeeperStatus.AVAILABLE);
        housekeeper.setActiveTaskCount(0);

        room1 = new Room("301", "DELUXE", RoomStatus.DIRTY);
        room1.setId(101L);

        room2 = new Room("302", "STANDARD", RoomStatus.DIRTY);
        room2.setId(102L);
    }

    @Test
    @DisplayName("Priority Queue: Dispatches HIGH priority task before NORMAL priority task")
    void testPriorityDispatchHighBeforeNormal() {
        CleaningTask normalTask = new CleaningTask(room1, TaskPriority.NORMAL, "Normal checkout");
        normalTask.setId(1L);
        normalTask.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        CleaningTask highTask = new CleaningTask(room2, TaskPriority.HIGH, "Inspection failed re-clean");
        highTask.setId(2L);
        highTask.setCreatedAt(LocalDateTime.now().minusMinutes(2)); // newer but HIGH priority

        when(housekeeperRepository.findByStatusOrderByActiveTaskCountAsc(HousekeeperStatus.AVAILABLE))
                .thenReturn(Collections.singletonList(housekeeper));

        // findPendingTasksOrdered returns HIGH priority tasks first
        when(cleaningTaskRepository.findPendingTasksOrdered(TaskStatus.PENDING))
                .thenReturn(Arrays.asList(highTask, normalTask));

        cleaningTaskService.dispatchToAvailableHousekeepers();

        // highTask was assigned to housekeeper
        assertEquals(TaskStatus.IN_PROGRESS, highTask.getStatus());
        assertEquals(housekeeper, highTask.getHousekeeper());
        assertNotNull(highTask.getAssignedAt());
        assertNotNull(highTask.getStartedAt());

        // Room 2 becomes IN_CLEANING
        assertEquals(RoomStatus.IN_CLEANING, room2.getStatus());

        // Housekeeper becomes BUSY
        assertEquals(HousekeeperStatus.BUSY, housekeeper.getStatus());
        assertEquals(1, housekeeper.getActiveTaskCount());

        // Normal task remains PENDING because only 1 housekeeper was available
        assertEquals(TaskStatus.PENDING, normalTask.getStatus());
    }

    @Test
    @DisplayName("Escalation: Long waiting NORMAL tasks escalate to HIGH priority")
    void testEscalationOfLongWaitingTasks() {
        CleaningTask oldNormalTask = new CleaningTask(room1, TaskPriority.NORMAL, "Waited long time");
        oldNormalTask.setId(3L);
        oldNormalTask.setCreatedAt(LocalDateTime.now().minusMinutes(20));

        when(cleaningTaskRepository.findTasksEligibleForEscalation(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(oldNormalTask));

        cleaningTaskService.escalateLongWaitingTasks();

        assertEquals(TaskPriority.HIGH, oldNormalTask.getPriority());
        verify(cleaningTaskRepository).save(oldNormalTask);
    }

    @Test
    @DisplayName("Task Completion: Marks task COMPLETED, room CLEANED, frees housekeeper, and triggers next dispatch")
    void testCompleteTask() {
        CleaningTask inProgressTask = new CleaningTask(room1, TaskPriority.NORMAL, "Routine");
        inProgressTask.setId(4L);
        inProgressTask.setStatus(TaskStatus.IN_PROGRESS);
        inProgressTask.setHousekeeper(housekeeper);
        inProgressTask.setStartedAt(LocalDateTime.now().minusMinutes(30));

        housekeeper.setStatus(HousekeeperStatus.BUSY);
        housekeeper.setActiveTaskCount(1);
        room1.setStatus(RoomStatus.IN_CLEANING);

        when(cleaningTaskRepository.findById(4L)).thenReturn(Optional.of(inProgressTask));
        when(cleaningTaskRepository.save(any(CleaningTask.class))).thenAnswer(i -> i.getArgument(0));

        CleaningTask completed = cleaningTaskService.completeTask(4L);

        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals(RoomStatus.CLEANED, room1.getStatus());
        assertNull(room1.getActiveCleaningTaskId());
        assertEquals(HousekeeperStatus.AVAILABLE, housekeeper.getStatus());
        assertEquals(0, housekeeper.getActiveTaskCount());

        verify(roomRepository).save(room1);
        verify(housekeeperRepository).save(housekeeper);
    }

    @Test
    @DisplayName("Task Completion: Retains OFFLINE status if housekeeper was set to OFFLINE")
    void testCompleteTaskRetainsOfflineStatus() {
        CleaningTask inProgressTask = new CleaningTask(room1, TaskPriority.NORMAL, "Routine");
        inProgressTask.setId(5L);
        inProgressTask.setStatus(TaskStatus.IN_PROGRESS);
        inProgressTask.setHousekeeper(housekeeper);
        inProgressTask.setStartedAt(LocalDateTime.now().minusMinutes(20));

        housekeeper.setStatus(HousekeeperStatus.OFFLINE);
        housekeeper.setActiveTaskCount(1);

        when(cleaningTaskRepository.findById(5L)).thenReturn(Optional.of(inProgressTask));
        when(cleaningTaskRepository.save(any(CleaningTask.class))).thenAnswer(i -> i.getArgument(0));

        CleaningTask completed = cleaningTaskService.completeTask(5L);

        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        assertEquals(HousekeeperStatus.OFFLINE, housekeeper.getStatus());
        assertEquals(0, housekeeper.getActiveTaskCount());
    }

    @Test
    @DisplayName("Task Completion fails when task is not IN_PROGRESS")
    void testCompleteTaskFailsIfNotInProgress() {
        CleaningTask pendingTask = new CleaningTask(room1, TaskPriority.NORMAL, "Routine");
        pendingTask.setId(6L);
        pendingTask.setStatus(TaskStatus.PENDING);

        when(cleaningTaskRepository.findById(6L)).thenReturn(Optional.of(pendingTask));

        assertThrows(com.hotel.housekeeptrack.exception.BusinessRuleException.class,
                () -> cleaningTaskService.completeTask(6L));
    }
}
