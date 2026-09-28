package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.CleaningTaskResponse;
import com.hotel.housekeeptrack.model.CleaningTask;
import com.hotel.housekeeptrack.service.CleaningTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Presenter component in Model-View-Presenter (MVP) architecture.
 * Mediates between CleaningTask Model (Service/Repository) and View (CleaningTaskResponse).
 */
@Component
public class CleaningTaskPresenter {

    private final CleaningTaskService cleaningTaskService;

    public CleaningTaskPresenter(CleaningTaskService cleaningTaskService) {
        this.cleaningTaskService = cleaningTaskService;
    }

    public ResponseEntity<List<CleaningTaskResponse>> presentTasks(Long roomId, Long housekeeperId) {
        List<CleaningTask> tasks;
        if (roomId != null) {
            tasks = cleaningTaskService.getTasksByRoom(roomId);
        } else if (housekeeperId != null) {
            tasks = cleaningTaskService.getTasksByHousekeeper(housekeeperId);
        } else {
            tasks = cleaningTaskService.getAllTasks();
        }

        List<CleaningTaskResponse> responses = tasks.stream()
                .map(CleaningTaskResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    public ResponseEntity<CleaningTaskResponse> presentTaskById(Long id) {
        CleaningTask task = cleaningTaskService.getTaskById(id);
        return ResponseEntity.ok(CleaningTaskResponse.fromEntity(task));
    }

    public ResponseEntity<CleaningTaskResponse> presentCompletedTask(Long id) {
        CleaningTask task = cleaningTaskService.completeTask(id);
        return ResponseEntity.ok(CleaningTaskResponse.fromEntity(task));
    }

    public ResponseEntity<Void> triggerAndPresentDispatch() {
        cleaningTaskService.dispatchToAvailableHousekeepers();
        return ResponseEntity.ok().build();
    }
}
