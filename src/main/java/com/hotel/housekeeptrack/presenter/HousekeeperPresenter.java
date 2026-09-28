package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.CreateHousekeeperRequest;
import com.hotel.housekeeptrack.dto.HousekeeperResponse;
import com.hotel.housekeeptrack.model.Housekeeper;
import com.hotel.housekeeptrack.model.HousekeeperStatus;
import com.hotel.housekeeptrack.service.HousekeeperService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Presenter component in Model-View-Presenter (MVP) architecture.
 * Mediates between Housekeeper Model (Service/Repository) and View (HousekeeperResponse).
 */
@Component
public class HousekeeperPresenter {

    private final HousekeeperService housekeeperService;

    public HousekeeperPresenter(HousekeeperService housekeeperService) {
        this.housekeeperService = housekeeperService;
    }

    public ResponseEntity<HousekeeperResponse> presentCreatedHousekeeper(CreateHousekeeperRequest request) {
        Housekeeper housekeeper = housekeeperService.createHousekeeper(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(HousekeeperResponse.fromEntity(housekeeper));
    }

    public ResponseEntity<List<HousekeeperResponse>> presentHousekeepers(HousekeeperStatus status) {
        List<Housekeeper> housekeepers = (status != null)
                ? housekeeperService.getHousekeepersByStatus(status)
                : housekeeperService.getAllHousekeepers();
        List<HousekeeperResponse> responses = housekeepers.stream()
                .map(HousekeeperResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    public ResponseEntity<HousekeeperResponse> presentHousekeeperById(Long id) {
        Housekeeper housekeeper = housekeeperService.getHousekeeperById(id);
        return ResponseEntity.ok(HousekeeperResponse.fromEntity(housekeeper));
    }

    public ResponseEntity<HousekeeperResponse> presentUpdatedStatus(Long id, Map<String, String> payload) {
        if (payload == null || !payload.containsKey("status") || payload.get("status") == null) {
            throw new com.hotel.housekeeptrack.exception.BusinessRuleException("Request body must contain 'status' field.");
        }
        String statusStr = payload.get("status").trim();
        HousekeeperStatus status;
        try {
            status = HousekeeperStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.hotel.housekeeptrack.exception.BusinessRuleException("Invalid status: " + statusStr + ". Allowed values: AVAILABLE, BUSY, OFFLINE");
        }
        Housekeeper housekeeper = housekeeperService.updateStatus(id, status);
        return ResponseEntity.ok(HousekeeperResponse.fromEntity(housekeeper));
    }

    public ResponseEntity<Void> presentDeletedHousekeeper(Long id) {
        housekeeperService.deleteHousekeeper(id);
        return ResponseEntity.noContent().build();
    }
}
