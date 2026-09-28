package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing cleaning tasks, lifecycle progression, priority assignment, and queue dispatch.
 */
@Service
public class CleaningTaskService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final HousekeeperRepository housekeeperRepository;
    private final RoomRepository roomRepository;
    private final AuditLogService auditLogService;

    @Value("${housekeeptrack.escalation.threshold-minutes:15}")
    private long escalationThresholdMinutes;

    public CleaningTaskService(CleaningTaskRepository cleaningTaskRepository,
                               HousekeeperRepository housekeeperRepository,
                               RoomRepository roomRepository,
                               AuditLogService auditLogService) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.housekeeperRepository = housekeeperRepository;
        this.roomRepository = roomRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Creates a new cleaning task for a room with specified priority and notes,
     * then attempts immediate dispatch to an available housekeeper.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "housekeeperWorkloads"}, allEntries = true)
    public CleaningTask createAndDispatchTask(Room room, TaskPriority priority, String notes) {
        CleaningTask task = new CleaningTask(room, priority, notes);
        task = cleaningTaskRepository.save(task);

        room.setActiveCleaningTaskId(task.getId());
        roomRepository.save(room);

        // Attempt immediate dispatch if an available housekeeper is present
        dispatchToAvailableHousekeepers();

        // Refresh task in case it was assigned during dispatch
        return cleaningTaskRepository.findById(task.getId()).orElse(task);
    }

    /**
     * Dispatches pending tasks to available housekeepers.
     * Enforces:
     * 1. Priority escalation for tasks waiting longer than threshold.
     * 2. Highest priority first (HIGH before NORMAL).
     * 3. Oldest FIFO by creation time within same priority band.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "housekeeperWorkloads"}, allEntries = true)
    public void dispatchToAvailableHousekeepers() {
        escalateLongWaitingTasks();

        List<Housekeeper> availableHousekeepers = housekeeperRepository.findByStatusOrderByActiveTaskCountAsc(HousekeeperStatus.AVAILABLE);
        if (availableHousekeepers.isEmpty()) {
            return;
        }

        List<CleaningTask> pendingTasks = cleaningTaskRepository.findPendingTasksOrdered(TaskStatus.PENDING);
        if (pendingTasks.isEmpty()) {
            return;
        }

        int housekeeperIndex = 0;
        for (CleaningTask task : pendingTasks) {
            if (housekeeperIndex >= availableHousekeepers.size()) {
                break;
            }

            Housekeeper housekeeper = availableHousekeepers.get(housekeeperIndex);

            // Assign task to housekeeper
            task.setHousekeeper(housekeeper);
            task.setStatus(TaskStatus.IN_PROGRESS);
            LocalDateTime now = LocalDateTime.now();
            task.setAssignedAt(now);
            task.setStartedAt(now);
            cleaningTaskRepository.save(task);

            // Update housekeeper state
            housekeeper.setStatus(HousekeeperStatus.BUSY);
            housekeeper.setActiveTaskCount(housekeeper.getActiveTaskCount() + 1);
            housekeeperRepository.save(housekeeper);

            // Update room state to IN_CLEANING
            Room room = task.getRoom();
            room.setStatus(RoomStatus.IN_CLEANING);
            room.setActiveCleaningTaskId(task.getId());
            roomRepository.save(room);

            // Record audit log entry
            auditLogService.log(AuditAction.TASK_ASSIGNED, "CleaningTask", task.getId(), housekeeper.getName(),
                    "Assigned task #" + task.getId() + " (" + task.getPriority() + " priority) for room " +
                            room.getRoomNumber() + " to housekeeper " + housekeeper.getName());

            housekeeperIndex++;
        }
    }

    /**
     * Marks a cleaning task as completed, transitions the room to CLEANED,
     * frees the housekeeper, and immediately dispatches the next highest-priority task.
     */
    @Transactional
    @CacheEvict(value = {"rooms", "systemSummary", "roomTurnaround", "housekeeperWorkloads"}, allEntries = true)
    public CleaningTask completeTask(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new com.hotel.housekeeptrack.exception.ResourceNotFoundException("CleaningTask not found with ID: " + taskId));

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new com.hotel.housekeeptrack.exception.BusinessRuleException("Only IN_PROGRESS tasks can be completed. Current status: " + task.getStatus());
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task = cleaningTaskRepository.save(task);

        // Update room status to CLEANED and clear active task reference
        Room room = task.getRoom();
        room.setStatus(RoomStatus.CLEANED);
        room.setActiveCleaningTaskId(null);
        roomRepository.save(room);

        // Free housekeeper only if currently BUSY (preserve OFFLINE status if set)
        Housekeeper housekeeper = task.getHousekeeper();
        if (housekeeper != null) {
            housekeeper.setActiveTaskCount(Math.max(0, housekeeper.getActiveTaskCount() - 1));
            if (housekeeper.getActiveTaskCount() == 0 && housekeeper.getStatus() == HousekeeperStatus.BUSY) {
                housekeeper.setStatus(HousekeeperStatus.AVAILABLE);
            }
            housekeeperRepository.save(housekeeper);
        }

        // Record audit log
        String worker = (housekeeper != null) ? housekeeper.getName() : "Staff";
        auditLogService.log(AuditAction.TASK_COMPLETED, "CleaningTask", task.getId(), worker,
                "Completed cleaning task #" + task.getId() + " for room " + room.getRoomNumber());

        // Newly available housekeeper can now take the next pending task in queue
        dispatchToAvailableHousekeepers();

        return task;
    }

    /**
     * Dynamically escalates pending tasks waiting longer than threshold to HIGH priority.
     */
    @Transactional
    public void escalateLongWaitingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(escalationThresholdMinutes);
        List<CleaningTask> eligible = cleaningTaskRepository.findTasksEligibleForEscalation(cutoff);
        for (CleaningTask task : eligible) {
            task.setPriority(TaskPriority.HIGH);
            cleaningTaskRepository.save(task);
        }
    }

    public List<CleaningTask> getAllTasks() {
        return cleaningTaskRepository.findAll();
    }

    public CleaningTask getTaskById(Long id) {
        return cleaningTaskRepository.findById(id)
                .orElseThrow(() -> new com.hotel.housekeeptrack.exception.ResourceNotFoundException("CleaningTask not found with ID: " + id));
    }

    public List<CleaningTask> getTasksByRoom(Long roomId) {
        return cleaningTaskRepository.findByRoomId(roomId);
    }

    public List<CleaningTask> getTasksByHousekeeper(Long housekeeperId) {
        return cleaningTaskRepository.findByHousekeeperId(housekeeperId);
    }
}
