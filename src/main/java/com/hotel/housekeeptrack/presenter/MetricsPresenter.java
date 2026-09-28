package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.HousekeeperWorkloadDto;
import com.hotel.housekeeptrack.dto.RoomTurnaroundDto;
import com.hotel.housekeeptrack.dto.SystemSummaryDto;
import com.hotel.housekeeptrack.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Presenter component in Model-View-Presenter (MVP) architecture.
 * Mediates between Metrics calculations and presentation responses.
 */
@Component
public class MetricsPresenter {

    private final MetricsService metricsService;

    public MetricsPresenter(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public ResponseEntity<List<HousekeeperWorkloadDto>> presentHousekeeperWorkloads() {
        return ResponseEntity.ok(metricsService.getHousekeeperWorkloadMetrics());
    }

    public ResponseEntity<List<RoomTurnaroundDto>> presentRoomTurnarounds() {
        return ResponseEntity.ok(metricsService.getRoomTurnaroundMetrics());
    }

    public ResponseEntity<SystemSummaryDto> presentSummary() {
        return ResponseEntity.ok(metricsService.getSystemSummary());
    }
}
