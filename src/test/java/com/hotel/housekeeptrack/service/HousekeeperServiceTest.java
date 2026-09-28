package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateHousekeeperRequest;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HousekeeperServiceTest {

    @Mock
    private HousekeeperRepository housekeeperRepository;

    @Mock
    private CleaningTaskService cleaningTaskService;

    @Mock
    private CleaningTaskRepository cleaningTaskRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private HousekeeperService housekeeperService;

    private Housekeeper housekeeper;

    @BeforeEach
    void setUp() {
        housekeeper = new Housekeeper("Bob Builder", "bob@hotel.com", "555-1234");
        housekeeper.setId(1L);
        housekeeper.setStatus(HousekeeperStatus.AVAILABLE);
        housekeeper.setActiveTaskCount(0);
    }

    @Test
    @DisplayName("Create housekeeper successfully and dispatch any pending tasks")
    void testCreateHousekeeperSuccess() {
        CreateHousekeeperRequest request = new CreateHousekeeperRequest("Bob Builder", "bob@hotel.com", "555-1234");
        when(housekeeperRepository.existsByEmail("bob@hotel.com")).thenReturn(false);
        when(housekeeperRepository.save(any(Housekeeper.class))).thenAnswer(i -> {
            Housekeeper h = i.getArgument(0);
            h.setId(1L);
            return h;
        });

        Housekeeper created = housekeeperService.createHousekeeper(request);
        assertNotNull(created);
        assertEquals("Bob Builder", created.getName());
        assertEquals("bob@hotel.com", created.getEmail());
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Create housekeeper fails on duplicate email")
    void testCreateHousekeeperDuplicateEmailFails() {
        CreateHousekeeperRequest request = new CreateHousekeeperRequest("Bob Builder", "bob@hotel.com", "555-1234");
        when(housekeeperRepository.existsByEmail("bob@hotel.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> housekeeperService.createHousekeeper(request));
        verify(housekeeperRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get housekeeper by ID fails when not found")
    void testGetHousekeeperByIdNotFound() {
        when(housekeeperRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> housekeeperService.getHousekeeperById(999L));
    }

    @Test
    @DisplayName("Update status to AVAILABLE succeeds when no active tasks")
    void testUpdateStatusToAvailableSuccess() {
        housekeeper.setStatus(HousekeeperStatus.OFFLINE);
        housekeeper.setActiveTaskCount(0);
        when(housekeeperRepository.findById(1L)).thenReturn(Optional.of(housekeeper));
        when(housekeeperRepository.save(any(Housekeeper.class))).thenReturn(housekeeper);

        Housekeeper updated = housekeeperService.updateStatus(1L, HousekeeperStatus.AVAILABLE);
        assertEquals(HousekeeperStatus.AVAILABLE, updated.getStatus());
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Update status to AVAILABLE fails if housekeeper has active tasks")
    void testUpdateStatusToAvailableFailsWhenBusy() {
        housekeeper.setStatus(HousekeeperStatus.BUSY);
        housekeeper.setActiveTaskCount(1);
        when(housekeeperRepository.findById(1L)).thenReturn(Optional.of(housekeeper));

        assertThrows(BusinessRuleException.class, () -> housekeeperService.updateStatus(1L, HousekeeperStatus.AVAILABLE));
        verify(cleaningTaskService, never()).dispatchToAvailableHousekeepers();
    }

    @Test
    @DisplayName("Update status to OFFLINE re-queues active tasks to PENDING, resets room to DIRTY, and dispatches")
    void testUpdateStatusToOfflineRequeuesActiveTasks() {
        housekeeper.setStatus(HousekeeperStatus.BUSY);
        housekeeper.setActiveTaskCount(1);

        Room room = new Room("101", "DELUXE", RoomStatus.IN_CLEANING);
        CleaningTask activeTask = new CleaningTask(room, TaskPriority.HIGH, "Cleaning");
        activeTask.setId(10L);
        activeTask.setStatus(TaskStatus.IN_PROGRESS);
        activeTask.setHousekeeper(housekeeper);

        when(housekeeperRepository.findById(1L)).thenReturn(Optional.of(housekeeper));
        when(cleaningTaskRepository.findByHousekeeperIdAndStatus(1L, TaskStatus.IN_PROGRESS))
                .thenReturn(Collections.singletonList(activeTask));
        when(housekeeperRepository.save(any(Housekeeper.class))).thenReturn(housekeeper);

        Housekeeper updated = housekeeperService.updateStatus(1L, HousekeeperStatus.OFFLINE);
        assertEquals(HousekeeperStatus.OFFLINE, updated.getStatus());
        assertEquals(0, updated.getActiveTaskCount());

        // Verify task reverted to PENDING and unassigned
        assertEquals(TaskStatus.PENDING, activeTask.getStatus());
        assertNull(activeTask.getHousekeeper());
        verify(cleaningTaskRepository).save(activeTask);

        // Verify room reverted to DIRTY
        assertEquals(RoomStatus.DIRTY, room.getStatus());
        verify(roomRepository).save(room);

        // Verify dispatch triggered for remaining staff
        verify(cleaningTaskService).dispatchToAvailableHousekeepers();
    }
}
