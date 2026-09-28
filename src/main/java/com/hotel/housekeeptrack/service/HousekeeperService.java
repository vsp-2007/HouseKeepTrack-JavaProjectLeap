package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateHousekeeperRequest;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.ResourceNotFoundException;
import com.hotel.housekeeptrack.model.AuditAction;
import com.hotel.housekeeptrack.model.CleaningTask;
import com.hotel.housekeeptrack.model.Housekeeper;
import com.hotel.housekeeptrack.model.HousekeeperStatus;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.model.TaskStatus;
import com.hotel.housekeeptrack.repository.CleaningTaskRepository;
import com.hotel.housekeeptrack.repository.HousekeeperRepository;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing housekeeper staff members, availability transitions, and audit logging.
 */
@Service
public class HousekeeperService {

    private final HousekeeperRepository housekeeperRepository;
    private final CleaningTaskService cleaningTaskService;
    private final CleaningTaskRepository cleaningTaskRepository;
    private final RoomRepository roomRepository;
    private final AuditLogService auditLogService;

    public HousekeeperService(HousekeeperRepository housekeeperRepository,
                              CleaningTaskService cleaningTaskService,
                              CleaningTaskRepository cleaningTaskRepository,
                              RoomRepository roomRepository,
                              AuditLogService auditLogService) {
        this.housekeeperRepository = housekeeperRepository;
        this.cleaningTaskService = cleaningTaskService;
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.roomRepository = roomRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @CacheEvict(value = {"housekeeperWorkloads", "systemSummary"}, allEntries = true)
    public Housekeeper createHousekeeper(CreateHousekeeperRequest request) {
        if (housekeeperRepository.existsByEmail(request.getEmail())) {
            throw new BusinessRuleException("Housekeeper with email " + request.getEmail() + " already exists.");
        }
        Housekeeper housekeeper = new Housekeeper(request.getName(), request.getEmail(), request.getPhone());
        housekeeper = housekeeperRepository.save(housekeeper);

        auditLogService.log(AuditAction.STAFF_REGISTERED, "Housekeeper", housekeeper.getId(), "Admin",
                "Registered housekeeper " + housekeeper.getName() + " (" + housekeeper.getEmail() + ")");

        // If newly added housekeeper is available, dispatch any existing pending tasks
        cleaningTaskService.dispatchToAvailableHousekeepers();

        return housekeeper;
    }

    public List<Housekeeper> getAllHousekeepers() {
        return housekeeperRepository.findAll();
    }

    public Housekeeper getHousekeeperById(Long id) {
        return housekeeperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Housekeeper not found with ID: " + id));
    }

    public List<Housekeeper> getHousekeepersByStatus(HousekeeperStatus status) {
        return housekeeperRepository.findByStatus(status);
    }

    /**
     * Updates housekeeper status.
     * - If transitioning to OFFLINE: re-queues any active IN_PROGRESS tasks back to PENDING,
     *   reverts room status to DIRTY, resets staff active task count, and triggers queue dispatch.
     * - If transitioning to AVAILABLE: verifies no active tasks are ongoing, then triggers queue dispatch.
     */
    @Transactional
    @CacheEvict(value = {"housekeeperWorkloads", "systemSummary", "rooms"}, allEntries = true)
    public Housekeeper updateStatus(Long id, HousekeeperStatus status) {
        Housekeeper housekeeper = getHousekeeperById(id);
        HousekeeperStatus oldStatus = housekeeper.getStatus();

        if (status == HousekeeperStatus.OFFLINE) {
            // Re-queue active tasks so rooms are not abandoned or stuck
            List<CleaningTask> activeTasks = cleaningTaskRepository.findByHousekeeperIdAndStatus(housekeeper.getId(), TaskStatus.IN_PROGRESS);
            for (CleaningTask task : activeTasks) {
                task.setStatus(TaskStatus.PENDING);
                task.setHousekeeper(null);
                task.setAssignedAt(null);
                task.setStartedAt(null);
                cleaningTaskRepository.save(task);

                Room room = task.getRoom();
                room.setStatus(RoomStatus.DIRTY);
                roomRepository.save(room);
            }

            housekeeper.setStatus(HousekeeperStatus.OFFLINE);
            housekeeper.setActiveTaskCount(0);
            housekeeper = housekeeperRepository.save(housekeeper);

            auditLogService.log(AuditAction.STAFF_STATUS_CHANGED, "Housekeeper", housekeeper.getId(), "Admin",
                    "Status updated from " + oldStatus + " to OFFLINE for housekeeper " + housekeeper.getName());

            // Re-dispatch orphaned tasks to other available staff
            cleaningTaskService.dispatchToAvailableHousekeepers();
            return housekeeper;
        }

        if (status == HousekeeperStatus.AVAILABLE) {
            if (housekeeper.getActiveTaskCount() != null && housekeeper.getActiveTaskCount() > 0) {
                throw new BusinessRuleException("Cannot set housekeeper to AVAILABLE while they have active cleaning tasks in progress.");
            }
            housekeeper.setStatus(HousekeeperStatus.AVAILABLE);
            housekeeper = housekeeperRepository.save(housekeeper);

            auditLogService.log(AuditAction.STAFF_STATUS_CHANGED, "Housekeeper", housekeeper.getId(), "Admin",
                    "Status updated from " + oldStatus + " to AVAILABLE for housekeeper " + housekeeper.getName());

            cleaningTaskService.dispatchToAvailableHousekeepers();
            return housekeeper;
        }

        housekeeper.setStatus(status);
        housekeeper = housekeeperRepository.save(housekeeper);

        auditLogService.log(AuditAction.STAFF_STATUS_CHANGED, "Housekeeper", housekeeper.getId(), "Admin",
                "Status updated from " + oldStatus + " to " + status + " for housekeeper " + housekeeper.getName());

        return housekeeper;
    }

    /**
     * Safely deletes a housekeeper:
     * Re-queues active tasks to PENDING and reverts affected rooms to DIRTY,
     * unlinks past tasks to preserve referential integrity, deletes housekeeper,
     * logs in AuditLog, and evicts caches.
     */
    @Transactional
    @CacheEvict(value = {"housekeeperWorkloads", "systemSummary", "rooms"}, allEntries = true)
    public void deleteHousekeeper(Long id) {
        Housekeeper housekeeper = getHousekeeperById(id);

        // 1. Re-queue active tasks to PENDING
        List<CleaningTask> activeTasks = cleaningTaskRepository.findByHousekeeperIdAndStatus(id, TaskStatus.IN_PROGRESS);
        for (CleaningTask task : activeTasks) {
            task.setStatus(TaskStatus.PENDING);
            task.setHousekeeper(null);
            task.setAssignedAt(null);
            task.setStartedAt(null);
            cleaningTaskRepository.save(task);

            Room room = task.getRoom();
            if (room != null) {
                room.setStatus(RoomStatus.DIRTY);
                roomRepository.save(room);
            }
        }

        // 2. Unlink all tasks referencing this housekeeper to avoid foreign key violation
        List<CleaningTask> allTasks = cleaningTaskRepository.findByHousekeeperId(id);
        for (CleaningTask task : allTasks) {
            task.setHousekeeper(null);
            cleaningTaskRepository.save(task);
        }

        // 3. Delete the housekeeper entity
        housekeeperRepository.delete(housekeeper);

        // 4. Log in AuditLog
        auditLogService.log(AuditAction.STAFF_DELETED, "Housekeeper", id, "Admin",
                "Deleted housekeeper " + housekeeper.getName() + " (" + housekeeper.getEmail() + ")");

        // 5. Re-dispatch queued tasks to other available staff
        cleaningTaskService.dispatchToAvailableHousekeepers();
    }
}
