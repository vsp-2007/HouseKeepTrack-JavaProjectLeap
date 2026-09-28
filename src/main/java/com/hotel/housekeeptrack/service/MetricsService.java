package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.HousekeeperWorkloadDto;
import com.hotel.housekeeptrack.dto.RoomTurnaroundDto;
import com.hotel.housekeeptrack.dto.SystemSummaryDto;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service calculating operational metrics:
 * - Housekeeper workload and cleaning task turnaround time (startedAt -> completedAt)
 * - Overall room turnaround time (dirtyAt -> readyAt)
 * - System aggregate health and queue status
 */
@Service
public class MetricsService {

    private final HousekeeperRepository housekeeperRepository;
    private final CleaningTaskRepository cleaningTaskRepository;
    private final RoomRepository roomRepository;

    public MetricsService(HousekeeperRepository housekeeperRepository,
                          CleaningTaskRepository cleaningTaskRepository,
                          RoomRepository roomRepository) {
        this.housekeeperRepository = housekeeperRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * Calculates workload and average cleaning turnaround time per housekeeper.
     */
    public List<HousekeeperWorkloadDto> getHousekeeperWorkloadMetrics() {
        List<Housekeeper> housekeepers = housekeeperRepository.findAll();
        List<CleaningTask> completedTasks = cleaningTaskRepository.findCompletedTasks();

        Map<Long, List<CleaningTask>> tasksByHousekeeper = completedTasks.stream()
                .filter(t -> t.getHousekeeper() != null)
                .collect(Collectors.groupingBy(t -> t.getHousekeeper().getId()));

        return housekeepers.stream().map(hk -> {
            List<CleaningTask> tasks = tasksByHousekeeper.getOrDefault(hk.getId(), Collections.emptyList());
            long completedCount = tasks.size();

            Double avgMinutes = null;
            if (completedCount > 0) {
                double totalMinutes = tasks.stream()
                        .mapToDouble(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds() / 60.0)
                        .sum();
                avgMinutes = Math.round((totalMinutes / completedCount) * 100.0) / 100.0;
            }

            return new HousekeeperWorkloadDto(
                    hk.getId(),
                    hk.getName(),
                    hk.getStatus(),
                    hk.getActiveTaskCount(),
                    completedCount,
                    avgMinutes
            );
        }).collect(Collectors.toList());
    }

    /**
     * Calculates overall room turnaround time (from dirtyAt to readyAt).
     */
    public List<RoomTurnaroundDto> getRoomTurnaroundMetrics() {
        List<Room> rooms = roomRepository.findAll();

        return rooms.stream().map(room -> {
            Double turnaroundMinutes = null;
            if (room.getDirtyAt() != null && room.getReadyAt() != null &&
                    !room.getReadyAt().isBefore(room.getDirtyAt())) {
                double diff = Math.max(0.0, Duration.between(room.getDirtyAt(), room.getReadyAt()).toSeconds() / 60.0);
                turnaroundMinutes = Math.round(diff * 100.0) / 100.0;
            }

            return new RoomTurnaroundDto(
                    room.getId(),
                    room.getRoomNumber(),
                    room.getRoomType(),
                    room.getStatus(),
                    room.getDirtyAt(),
                    room.getReadyAt(),
                    turnaroundMinutes
            );
        }).collect(Collectors.toList());
    }

    /**
     * Aggregate system summary report.
     */
    public SystemSummaryDto getSystemSummary() {
        SystemSummaryDto summary = new SystemSummaryDto();

        List<Room> rooms = roomRepository.findAll();
        summary.setTotalRooms(rooms.size());
        Map<String, Long> roomStatusMap = Arrays.stream(RoomStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        st -> rooms.stream().filter(r -> r.getStatus() == st).count()
                ));
        summary.setRoomsByStatus(roomStatusMap);

        List<Housekeeper> housekeepers = housekeeperRepository.findAll();
        summary.setTotalHousekeepers(housekeepers.size());
        Map<String, Long> housekeeperStatusMap = Arrays.stream(HousekeeperStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        st -> housekeepers.stream().filter(h -> h.getStatus() == st).count()
                ));
        summary.setHousekeepersByStatus(housekeeperStatusMap);

        summary.setPendingCleaningTasks(cleaningTaskRepository.countByStatus(TaskStatus.PENDING));
        summary.setInProgressCleaningTasks(cleaningTaskRepository.countByStatus(TaskStatus.IN_PROGRESS));
        summary.setCompletedCleaningTasks(cleaningTaskRepository.countByStatus(TaskStatus.COMPLETED));

        // Average overall room turnaround
        List<RoomTurnaroundDto> roomTurnarounds = getRoomTurnaroundMetrics();
        List<Double> validRoomMins = roomTurnarounds.stream()
                .map(RoomTurnaroundDto::getTurnaroundMinutes)
                .filter(Objects::nonNull)
                .toList();

        if (!validRoomMins.isEmpty()) {
            double avg = validRoomMins.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.setAvgRoomTurnaroundMinutes(Math.round(avg * 100.0) / 100.0);
        }

        // Average overall cleaning turnaround
        List<CleaningTask> completedTasks = cleaningTaskRepository.findCompletedTasks();
        if (!completedTasks.isEmpty()) {
            double avg = completedTasks.stream()
                    .mapToDouble(t -> Duration.between(t.getStartedAt(), t.getCompletedAt()).toSeconds() / 60.0)
                    .average().orElse(0.0);
            summary.setAvgCleaningTaskTurnaroundMinutes(Math.round(avg * 100.0) / 100.0);
        }

        return summary;
    }
}
