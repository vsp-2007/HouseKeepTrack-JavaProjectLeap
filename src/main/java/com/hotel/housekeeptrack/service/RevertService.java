package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.AuditLogResponse;
import com.hotel.housekeeptrack.dto.RevertActionResponse;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.model.*;
import com.hotel.housekeeptrack.repository.*;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing mistake-handling revert operations.
 * Evaluates operational audit history, determines the most recent revertible action,
 * rolls back domain state, logs the reversion event, and clears caches.
 */
@Service
public class RevertService {

    private static final Set<AuditAction> REVERTIBLE_ACTIONS = EnumSet.of(
            AuditAction.CHECK_IN,
            AuditAction.CHECKOUT,
            AuditAction.MARK_READY,
            AuditAction.SEND_TO_CLEANING,
            AuditAction.STAFF_STATUS_CHANGED,
            AuditAction.INSPECTION_PASSED,
            AuditAction.INSPECTION_FAILED
    );

    private static final Pattern REVERTED_LOG_ID_PATTERN = Pattern.compile("AuditLog #(\\d+)");
    private static final Pattern ROOM_NUMBER_PATTERN = Pattern.compile("(?i)room\\s+([A-Za-z0-9_-]+)");

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final RoomRepository roomRepository;
    private final CleaningTaskRepository cleaningTaskRepository;
    private final HousekeeperRepository housekeeperRepository;
    private final InspectionRepository inspectionRepository;
    private final CleaningTaskService cleaningTaskService;
    private final CacheManager cacheManager;

    public RevertService(AuditLogRepository auditLogRepository,
                         AuditLogService auditLogService,
                         RoomRepository roomRepository,
                         CleaningTaskRepository cleaningTaskRepository,
                         HousekeeperRepository housekeeperRepository,
                         InspectionRepository inspectionRepository,
                         CleaningTaskService cleaningTaskService,
                         CacheManager cacheManager) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.roomRepository = roomRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.housekeeperRepository = housekeeperRepository;
        this.inspectionRepository = inspectionRepository;
        this.cleaningTaskService = cleaningTaskService;
        this.cacheManager = cacheManager;
    }

    /**
     * Finds and reverts the latest revertible action in the audit log.
     * Skips already-reverted actions and actions whose target entities have been deleted,
     * ensuring mistake-handling operations remain resilient and self-healing.
     */
    @Transactional
    public RevertActionResponse revertLastAction() {
        List<AuditLog> allLogs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));

        // Collect all audit log IDs that have already been reverted
        Set<Long> alreadyRevertedIds = new HashSet<>();
        for (AuditLog log : allLogs) {
            if (log.getAction() == AuditAction.REVERTED && log.getDetails() != null) {
                Matcher matcher = REVERTED_LOG_ID_PATTERN.matcher(log.getDetails());
                while (matcher.find()) {
                    try {
                        alreadyRevertedIds.add(Long.parseLong(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // Find the most recent revertible action not yet reverted whose entity is still present
        AuditLog targetLog = null;
        for (AuditLog log : allLogs) {
            if (REVERTIBLE_ACTIONS.contains(log.getAction()) && !alreadyRevertedIds.contains(log.getId())) {
                if (isTargetEntityPresent(log)) {
                    targetLog = log;
                    break;
                }
            }
        }

        if (targetLog == null) {
            throw new BusinessRuleException("No revertible actions found in audit history.");
        }

        String summary = executeReversion(targetLog);

        // Record a new AuditLog marking the action as REVERTED
        AuditLog revertAuditLog = auditLogService.log(
                AuditAction.REVERTED,
                targetLog.getEntityType(),
                targetLog.getEntityId(),
                "Admin",
                "Reverted " + targetLog.getAction() + " (AuditLog #" + targetLog.getId() + "): " + summary
        );

        // Invalidate Spring Caches
        evictAllCaches();

        return new RevertActionResponse(
                summary,
                targetLog.getAction(),
                targetLog.getEntityId(),
                targetLog.getEntityType(),
                AuditLogResponse.fromEntity(revertAuditLog)
        );
    }

    private boolean isTargetEntityPresent(AuditLog log) {
        if (log.getEntityId() == null) {
            return false;
        }
        AuditAction action = log.getAction();
        if (action == AuditAction.CHECK_IN || action == AuditAction.CHECKOUT ||
                action == AuditAction.MARK_READY || action == AuditAction.SEND_TO_CLEANING) {
            return roomRepository.existsById(log.getEntityId());
        }
        if (action == AuditAction.STAFF_STATUS_CHANGED) {
            return housekeeperRepository.existsById(log.getEntityId());
        }
        if (action == AuditAction.INSPECTION_PASSED || action == AuditAction.INSPECTION_FAILED) {
            if (inspectionRepository.existsById(log.getEntityId())) {
                return true;
            }
            String roomNum = extractRoomNumber(log.getDetails());
            return roomNum != null && roomRepository.existsByRoomNumber(roomNum);
        }
        return true;
    }

    private String extractRoomNumber(String details) {
        if (details == null) {
            return null;
        }
        Matcher m = ROOM_NUMBER_PATTERN.matcher(details);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String executeReversion(AuditLog targetLog) {
        AuditAction action = targetLog.getAction();
        Long entityId = targetLog.getEntityId();

        switch (action) {
            case CHECK_IN:
                return revertCheckIn(entityId);

            case CHECKOUT:
                return revertCheckout(entityId);

            case MARK_READY:
                return revertMarkReady(entityId);

            case SEND_TO_CLEANING:
                return revertSendToCleaning(targetLog);

            case STAFF_STATUS_CHANGED:
                return revertStaffStatusChanged(targetLog);

            case INSPECTION_PASSED:
                return revertInspectionPassed(targetLog);

            case INSPECTION_FAILED:
                return revertInspectionFailed(targetLog);

            default:
                throw new BusinessRuleException("Action " + action + " cannot be automatically reverted.");
        }
    }

    private String revertCheckIn(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleException("Cannot revert: Room #" + roomId + " not found."));

        room.setStatus(RoomStatus.READY);
        room.setReadyAt(LocalDateTime.now());
        roomRepository.save(room);

        return "Room " + room.getRoomNumber() + " check-in reverted: status restored to READY.";
    }

    private String revertCheckout(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleException("Cannot revert: Room #" + roomId + " not found."));

        room.setStatus(RoomStatus.OCCUPIED);
        room.setDirtyAt(null);

        // Cancel generated cleaning task
        cancelActiveTaskForRoom(room);

        room.setActiveCleaningTaskId(null);
        roomRepository.save(room);

        return "Room " + room.getRoomNumber() + " checkout reverted: status restored to OCCUPIED and cleaning task cancelled.";
    }

    private String revertMarkReady(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleException("Cannot revert: Room #" + roomId + " not found."));

        room.setStatus(RoomStatus.INSPECTED);
        room.setReadyAt(null);
        roomRepository.save(room);

        return "Room " + room.getRoomNumber() + " mark-ready reverted: status restored to INSPECTED.";
    }

    private String revertSendToCleaning(AuditLog targetLog) {
        Long roomId = targetLog.getEntityId();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleException("Cannot revert: Room #" + roomId + " not found."));

        // Determine if previous state was INSPECTED or CLEANED
        boolean wasInspected = false;
        if (targetLog.getDetails() != null) {
            if (targetLog.getDetails().contains("from INSPECTED")) {
                wasInspected = true;
            } else if (targetLog.getDetails().contains("from CLEANED")) {
                wasInspected = false;
            }
        }

        // Remove the FAILED inspection record created by sendBackToCleaning
        Optional<Inspection> lastInsp = inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(room.getId());
        if (lastInsp.isPresent() && lastInsp.get().getResult() == InspectionResult.FAILED) {
            inspectionRepository.delete(lastInsp.get());
        }

        // If not explicit in details, inspect the top remaining inspection record
        if (!wasInspected) {
            Optional<Inspection> prevInsp = inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(room.getId());
            if (prevInsp.isPresent() && prevInsp.get().getResult() == InspectionResult.PASSED) {
                wasInspected = true;
            }
        }

        RoomStatus restoredStatus = wasInspected ? RoomStatus.INSPECTED : RoomStatus.CLEANED;
        room.setStatus(restoredStatus);
        room.setLastInspectionResult(wasInspected ? InspectionResult.PASSED : null);
        room.setReadyAt(null);

        // Cancel generated re-cleaning task
        cancelActiveTaskForRoom(room);
        room.setActiveCleaningTaskId(null);

        roomRepository.save(room);

        return "Room " + room.getRoomNumber() + " send-to-cleaning reverted: status restored to " + restoredStatus + ".";
    }

    private String revertStaffStatusChanged(AuditLog targetLog) {
        Housekeeper hk = housekeeperRepository.findById(targetLog.getEntityId())
                .orElseThrow(() -> new BusinessRuleException("Cannot revert: Housekeeper #" + targetLog.getEntityId() + " not found."));

        HousekeeperStatus targetPrevStatus = null;
        if (targetLog.getDetails() != null) {
            Matcher m = Pattern.compile("from (\\w+) to (\\w+)").matcher(targetLog.getDetails());
            if (m.find()) {
                try {
                    targetPrevStatus = HousekeeperStatus.valueOf(m.group(1));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (targetPrevStatus == null) {
            targetPrevStatus = (hk.getStatus() == HousekeeperStatus.OFFLINE)
                    ? HousekeeperStatus.AVAILABLE
                    : HousekeeperStatus.OFFLINE;
        }

        if (targetPrevStatus == HousekeeperStatus.OFFLINE) {
            // Re-queue active tasks so rooms are not abandoned or stuck
            List<CleaningTask> activeTasks = cleaningTaskRepository.findByHousekeeperIdAndStatus(hk.getId(), TaskStatus.IN_PROGRESS);
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
            hk.setActiveTaskCount(0);
            hk.setStatus(HousekeeperStatus.OFFLINE);
            housekeeperRepository.save(hk);
            cleaningTaskService.dispatchToAvailableHousekeepers();
        } else if (targetPrevStatus == HousekeeperStatus.AVAILABLE) {
            hk.setStatus(HousekeeperStatus.AVAILABLE);
            housekeeperRepository.save(hk);
            cleaningTaskService.dispatchToAvailableHousekeepers();
        } else {
            hk.setStatus(targetPrevStatus);
            housekeeperRepository.save(hk);
        }

        return "Housekeeper " + hk.getName() + " status reverted to " + targetPrevStatus + ".";
    }

    private String revertInspectionPassed(AuditLog targetLog) {
        Inspection inspection = inspectionRepository.findById(targetLog.getEntityId()).orElse(null);
        Room room = null;
        if (inspection != null) {
            room = inspection.getRoom();
        } else {
            String roomNum = extractRoomNumber(targetLog.getDetails());
            if (roomNum != null) {
                room = roomRepository.findByRoomNumber(roomNum).orElse(null);
            }
        }

        if (room == null) {
            throw new BusinessRuleException("Cannot revert: Target room for inspection #" + targetLog.getEntityId() + " not found.");
        }

        if (inspection != null) {
            inspectionRepository.delete(inspection);
        }

        // Look at previous inspection if any
        Optional<Inspection> prevInsp = inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(room.getId());
        if (prevInsp.isPresent()) {
            room.setLastInspectionResult(prevInsp.get().getResult());
        } else {
            room.setLastInspectionResult(null);
        }

        room.setStatus(RoomStatus.CLEANED);
        roomRepository.save(room);

        return "Inspection PASSED reverted for Room " + room.getRoomNumber() + ": status restored to CLEANED.";
    }

    private String revertInspectionFailed(AuditLog targetLog) {
        Inspection inspection = inspectionRepository.findById(targetLog.getEntityId()).orElse(null);
        Room room = null;
        if (inspection != null) {
            room = inspection.getRoom();
        } else {
            String roomNum = extractRoomNumber(targetLog.getDetails());
            if (roomNum != null) {
                room = roomRepository.findByRoomNumber(roomNum).orElse(null);
            }
        }

        if (room == null) {
            throw new BusinessRuleException("Cannot revert: Target room for inspection #" + targetLog.getEntityId() + " not found.");
        }

        if (inspection != null) {
            inspectionRepository.delete(inspection);
        }

        cancelActiveTaskForRoom(room);
        room.setActiveCleaningTaskId(null);

        // Check if there was an earlier inspection (e.g. PASSED)
        Optional<Inspection> prevInsp = inspectionRepository.findTopByRoomIdOrderByInspectedAtDesc(room.getId());
        if (prevInsp.isPresent() && prevInsp.get().getResult() == InspectionResult.PASSED) {
            room.setStatus(RoomStatus.INSPECTED);
            room.setLastInspectionResult(InspectionResult.PASSED);
        } else {
            room.setStatus(RoomStatus.CLEANED);
            room.setLastInspectionResult(prevInsp.map(Inspection::getResult).orElse(null));
        }

        roomRepository.save(room);

        return "Inspection FAILED reverted for Room " + room.getRoomNumber() + ": status restored to " + room.getStatus() + " and re-cleaning task cancelled.";
    }

    private void cancelActiveTaskForRoom(Room room) {
        CleaningTask task = null;
        if (room.getActiveCleaningTaskId() != null) {
            task = cleaningTaskRepository.findById(room.getActiveCleaningTaskId()).orElse(null);
        }
        if (task == null) {
            List<CleaningTask> tasks = cleaningTaskRepository.findByRoomId(room.getId());
            for (int i = tasks.size() - 1; i >= 0; i--) {
                CleaningTask t = tasks.get(i);
                if (t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS) {
                    task = t;
                    break;
                }
            }
        }

        if (task != null && task.getStatus() != TaskStatus.COMPLETED) {
            Housekeeper hk = task.getHousekeeper();
            boolean hkFreed = false;
            if (hk != null) {
                hk.setActiveTaskCount(Math.max(0, hk.getActiveTaskCount() - 1));
                if (hk.getActiveTaskCount() == 0 && hk.getStatus() == HousekeeperStatus.BUSY) {
                    hk.setStatus(HousekeeperStatus.AVAILABLE);
                    hkFreed = true;
                }
                housekeeperRepository.save(hk);
            }
            task.setStatus(TaskStatus.CANCELLED);
            task.setHousekeeper(null);
            cleaningTaskRepository.save(task);

            if (hkFreed) {
                cleaningTaskService.dispatchToAvailableHousekeepers();
            }
        }
    }

    private void evictAllCaches() {
        if (cacheManager != null) {
            for (String cacheName : cacheManager.getCacheNames()) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
        }
    }
}
