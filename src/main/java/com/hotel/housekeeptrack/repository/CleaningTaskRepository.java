package com.hotel.housekeeptrack.repository;

import com.hotel.housekeeptrack.model.CleaningTask;
import com.hotel.housekeeptrack.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CleaningTaskRepository extends JpaRepository<CleaningTask, Long> {

    List<CleaningTask> findByRoomId(Long roomId);

    List<CleaningTask> findByHousekeeperId(Long housekeeperId);

    List<CleaningTask> findByHousekeeperIdAndStatus(Long housekeeperId, TaskStatus status);

    /**
     * Finds pending tasks ordered by priority (HIGH before NORMAL) then oldest FIFO by assignedAt / createdAt.
     */
    @Query("SELECT t FROM CleaningTask t WHERE t.status = :status ORDER BY CASE WHEN t.priority = com.hotel.housekeeptrack.model.TaskPriority.HIGH THEN 0 ELSE 1 END ASC, COALESCE(t.assignedAt, t.createdAt) ASC, t.createdAt ASC")
    List<CleaningTask> findPendingTasksOrdered(@Param("status") TaskStatus status);

    /**
     * Finds tasks eligible for wait-time priority escalation based on createdAt or room dirtyAt.
     */
    @Query("SELECT t FROM CleaningTask t WHERE t.status = com.hotel.housekeeptrack.model.TaskStatus.PENDING AND t.priority = com.hotel.housekeeptrack.model.TaskPriority.NORMAL AND (t.createdAt <= :cutoff OR (t.room.dirtyAt IS NOT NULL AND t.room.dirtyAt <= :cutoff))")
    List<CleaningTask> findTasksEligibleForEscalation(@Param("cutoff") LocalDateTime cutoff);

    /**
     * All completed tasks for turnaround metrics.
     */
    @Query("SELECT t FROM CleaningTask t WHERE t.status = com.hotel.housekeeptrack.model.TaskStatus.COMPLETED AND t.startedAt IS NOT NULL AND t.completedAt IS NOT NULL")
    List<CleaningTask> findCompletedTasks();

    /**
     * Completed tasks for a specific housekeeper.
     */
    @Query("SELECT t FROM CleaningTask t WHERE t.housekeeper.id = :housekeeperId AND t.status = com.hotel.housekeeptrack.model.TaskStatus.COMPLETED AND t.startedAt IS NOT NULL AND t.completedAt IS NOT NULL")
    List<CleaningTask> findCompletedTasksByHousekeeper(@Param("housekeeperId") Long housekeeperId);

    long countByStatus(TaskStatus status);
}
