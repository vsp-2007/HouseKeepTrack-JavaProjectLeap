package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.CreateInspectionRequest;
import com.hotel.housekeeptrack.dto.InspectionResponse;
import com.hotel.housekeeptrack.presenter.InspectionPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spring MVC REST Controller.
 * Dispatches incoming HTTP requests and coordinates view model responses.
 */
@RestController
@RequestMapping("/api")
public class InspectionController {

    private final InspectionPresenter inspectionPresenter;

    public InspectionController(InspectionPresenter inspectionPresenter) {
        this.inspectionPresenter = inspectionPresenter;
    }

    /**
     * Inspect a CLEANED room:
     * - PASSED: room transitions to INSPECTED (eligible for READY)
     * - FAILED: room reverts to DIRTY, generates HIGH-priority re-cleaning task, triggers auto-assignment
     */
    @PostMapping("/rooms/{roomId}/inspections")
    public ResponseEntity<InspectionResponse> inspectRoom(@PathVariable Long roomId,
                                                          @Valid @RequestBody CreateInspectionRequest request) {
        return inspectionPresenter.presentInspection(roomId, request);
    }

    @GetMapping("/rooms/{roomId}/inspections")
    public ResponseEntity<List<InspectionResponse>> getInspectionsForRoom(@PathVariable Long roomId) {
        return inspectionPresenter.presentInspectionsForRoom(roomId);
    }

    @GetMapping("/inspections")
    public ResponseEntity<List<InspectionResponse>> getAllInspections() {
        return inspectionPresenter.presentAllInspections();
    }
}
