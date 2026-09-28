package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.CreateHousekeeperRequest;
import com.hotel.housekeeptrack.dto.HousekeeperResponse;
import com.hotel.housekeeptrack.model.HousekeeperStatus;
import com.hotel.housekeeptrack.presenter.HousekeeperPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Spring MVC REST Controller.
 * Dispatches incoming HTTP requests and coordinates view model responses.
 */
@RestController
@RequestMapping("/api/housekeepers")
public class HousekeeperController {

    private final HousekeeperPresenter housekeeperPresenter;

    public HousekeeperController(HousekeeperPresenter housekeeperPresenter) {
        this.housekeeperPresenter = housekeeperPresenter;
    }

    @PostMapping
    public ResponseEntity<HousekeeperResponse> createHousekeeper(@Valid @RequestBody CreateHousekeeperRequest request) {
        return housekeeperPresenter.presentCreatedHousekeeper(request);
    }

    @GetMapping
    public ResponseEntity<List<HousekeeperResponse>> getAllHousekeepers(@RequestParam(required = false) HousekeeperStatus status) {
        return housekeeperPresenter.presentHousekeepers(status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HousekeeperResponse> getHousekeeperById(@PathVariable Long id) {
        return housekeeperPresenter.presentHousekeeperById(id);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<HousekeeperResponse> updateStatus(@PathVariable Long id,
                                                            @RequestBody Map<String, String> payload) {
        return housekeeperPresenter.presentUpdatedStatus(id, payload);
    }

    /**
     * Safely deletes a housekeeper: re-queues active tasks to PENDING, logs in AuditLog, evicts cache.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHousekeeper(@PathVariable Long id) {
        return housekeeperPresenter.presentDeletedHousekeeper(id);
    }
}
