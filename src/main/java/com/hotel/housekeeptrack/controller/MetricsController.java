package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.HousekeeperWorkloadDto;
import com.hotel.housekeeptrack.dto.RoomTurnaroundDto;
import com.hotel.housekeeptrack.dto.SystemSummaryDto;
import com.hotel.housekeeptrack.presenter.MetricsPresenter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Spring MVC REST Controller.
 * Dispatches incoming HTTP requests and coordinates view model responses.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsPresenter metricsPresenter;

    public MetricsController(MetricsPresenter metricsPresenter) {
        this.metricsPresenter = metricsPresenter;
    }

    @GetMapping("/housekeepers")
    public ResponseEntity<List<HousekeeperWorkloadDto>> getHousekeeperWorkloads() {
        return metricsPresenter.presentHousekeeperWorkloads();
    }

    @GetMapping("/room-turnaround")
    public ResponseEntity<List<RoomTurnaroundDto>> getRoomTurnarounds() {
        return metricsPresenter.presentRoomTurnarounds();
    }

    @GetMapping("/summary")
    public ResponseEntity<SystemSummaryDto> getSummary() {
        return metricsPresenter.presentSummary();
    }
}
