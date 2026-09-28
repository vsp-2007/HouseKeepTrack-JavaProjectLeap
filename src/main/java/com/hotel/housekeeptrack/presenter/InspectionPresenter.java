package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.CreateInspectionRequest;
import com.hotel.housekeeptrack.dto.InspectionResponse;
import com.hotel.housekeeptrack.model.Inspection;
import com.hotel.housekeeptrack.service.InspectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Presenter component in Model-View-Presenter (MVP) architecture.
 * Mediates between Inspection Model (Service/Repository) and View (InspectionResponse).
 */
@Component
public class InspectionPresenter {

    private final InspectionService inspectionService;

    public InspectionPresenter(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    public ResponseEntity<InspectionResponse> presentInspection(Long roomId, CreateInspectionRequest request) {
        Inspection inspection = inspectionService.inspectRoom(roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(InspectionResponse.fromEntity(inspection));
    }

    public ResponseEntity<List<InspectionResponse>> presentInspectionsForRoom(Long roomId) {
        List<Inspection> inspections = inspectionService.getInspectionsForRoom(roomId);
        List<InspectionResponse> responses = inspections.stream()
                .map(InspectionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    public ResponseEntity<List<InspectionResponse>> presentAllInspections() {
        List<Inspection> inspections = inspectionService.getAllInspections();
        List<InspectionResponse> responses = inspections.stream()
                .map(InspectionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
