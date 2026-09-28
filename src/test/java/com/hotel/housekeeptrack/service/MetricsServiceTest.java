package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.HousekeeperWorkloadDto;
import com.hotel.housekeeptrack.dto.RoomTurnaroundDto;
import com.hotel.housekeeptrack.dto.SystemSummaryDto;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private HousekeeperRepository housekeeperRepository;

    @Mock
    private CleaningTaskRepository cleaningTaskRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private MetricsService metricsService;

    @Test
    @DisplayName("Housekeeper Workload & Cleaning Turnaround Metrics calculation")
    void testHousekeeperWorkloadMetrics() {
        Housekeeper hk = new Housekeeper("Alice Smith", "alice@hotel.com", "555-0100");
        hk.setId(1L);
        hk.setStatus(HousekeeperStatus.AVAILABLE);
        hk.setActiveTaskCount(0);

        Room room = new Room("101", "DELUXE", RoomStatus.READY);
        room.setId(10L);

        LocalDateTime now = LocalDateTime.now();
        CleaningTask task1 = new CleaningTask(room, TaskPriority.NORMAL, "Note");
        task1.setId(100L);
        task1.setHousekeeper(hk);
        task1.setStatus(TaskStatus.COMPLETED);
        task1.setStartedAt(now.minusMinutes(30));
        task1.setCompletedAt(now); // 30 minutes duration

        when(housekeeperRepository.findAll()).thenReturn(Collections.singletonList(hk));
        when(cleaningTaskRepository.findCompletedTasks()).thenReturn(Collections.singletonList(task1));

        List<HousekeeperWorkloadDto> results = metricsService.getHousekeeperWorkloadMetrics();
        assertNotNull(results);
        assertEquals(1, results.size());

        HousekeeperWorkloadDto dto = results.get(0);
        assertEquals("Alice Smith", dto.getHousekeeperName());
        assertEquals(1L, dto.getCompletedTaskCount());
        assertNotNull(dto.getAvgTurnaroundMinutes());
        assertEquals(30.0, dto.getAvgTurnaroundMinutes(), 0.1);
    }

    @Test
    @DisplayName("Room Turnaround Metrics calculation (dirtyAt to readyAt)")
    void testRoomTurnaroundMetrics() {
        LocalDateTime now = LocalDateTime.now();
        Room room = new Room("202", "STANDARD", RoomStatus.READY);
        room.setId(20L);
        room.setDirtyAt(now.minusMinutes(60));
        room.setReadyAt(now);

        when(roomRepository.findAll()).thenReturn(Collections.singletonList(room));

        List<RoomTurnaroundDto> results = metricsService.getRoomTurnaroundMetrics();
        assertNotNull(results);
        assertEquals(1, results.size());

        RoomTurnaroundDto dto = results.get(0);
        assertEquals("202", dto.getRoomNumber());
        assertNotNull(dto.getTurnaroundMinutes());
        assertEquals(60.0, dto.getTurnaroundMinutes(), 0.1);
    }

    @Test
    @DisplayName("System Summary aggregation")
    void testSystemSummary() {
        Room r1 = new Room("101", "DELUXE", RoomStatus.READY);
        r1.setId(1L);
        r1.setDirtyAt(LocalDateTime.now().minusMinutes(45));
        r1.setReadyAt(LocalDateTime.now());

        Room r2 = new Room("102", "DELUXE", RoomStatus.OCCUPIED);
        r2.setId(2L);

        Housekeeper hk = new Housekeeper("Bob", "bob@hotel.com", "555-0200");
        hk.setId(1L);
        hk.setStatus(HousekeeperStatus.AVAILABLE);

        when(roomRepository.findAll()).thenReturn(Arrays.asList(r1, r2));
        when(housekeeperRepository.findAll()).thenReturn(Collections.singletonList(hk));
        when(cleaningTaskRepository.countByStatus(TaskStatus.PENDING)).thenReturn(0L);
        when(cleaningTaskRepository.countByStatus(TaskStatus.IN_PROGRESS)).thenReturn(0L);
        when(cleaningTaskRepository.countByStatus(TaskStatus.COMPLETED)).thenReturn(1L);

        SystemSummaryDto summary = metricsService.getSystemSummary();
        assertNotNull(summary);
        assertEquals(2, summary.getTotalRooms());
        assertEquals(1, summary.getTotalHousekeepers());
        assertEquals(1L, summary.getRoomsByStatus().get("READY"));
        assertEquals(1L, summary.getRoomsByStatus().get("OCCUPIED"));
    }
}
