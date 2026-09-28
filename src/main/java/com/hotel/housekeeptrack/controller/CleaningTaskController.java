package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.CleaningTaskResponse;
import com.hotel.housekeeptrack.presenter.CleaningTaskPresenter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spring MVC REST Controller.
 * Dispatches incoming HTTP requests and coordinates view model responses.
 */
@RestController
@RequestMapping("/api/tasks")
public class CleaningTaskController {

    private final CleaningTaskPresenter cleaningTaskPresenter;

    public CleaningTaskController(CleaningTaskPresenter cleaningTaskPresenter) {
        this.cleaningTaskPresenter = cleaningTaskPresenter;
    }

    @GetMapping
    public ResponseEntity<List<CleaningTaskResponse>> getAllTasks(@RequestParam(required = false) Long roomId,
                                                                 @RequestParam(required = false) Long housekeeperId) {
        return cleaningTaskPresenter.presentTasks(roomId, housekeeperId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CleaningTaskResponse> getTaskById(@PathVariable Long id) {
        return cleaningTaskPresenter.presentTaskById(id);
    }

    /**
     * Housekeeper completes cleaning task:
     * - Task completed
     * - Room transitions to CLEANED
     * - Housekeeper status becomes AVAILABLE
     * - Next pending task from priority queue is automatically dispatched to this housekeeper
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<CleaningTaskResponse> completeTask(@PathVariable Long id) {
        return cleaningTaskPresenter.presentCompletedTask(id);
    }

    /**
     * Explicit trigger for queue dispatch.
     */
    @PostMapping("/dispatch")
    public ResponseEntity<Void> triggerDispatch() {
        return cleaningTaskPresenter.triggerAndPresentDispatch();
    }
}
