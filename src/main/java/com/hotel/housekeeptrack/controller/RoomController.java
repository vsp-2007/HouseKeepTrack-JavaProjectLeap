package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.dto.RoomResponse;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.presenter.RoomPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spring MVC REST Controller.
 * Dispatches incoming HTTP requests and coordinates view model responses.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomPresenter roomPresenter;

    public RoomController(RoomPresenter roomPresenter) {
        this.roomPresenter = roomPresenter;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomPresenter.presentCreatedRoom(request);
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms(@RequestParam(required = false) RoomStatus status) {
        return roomPresenter.presentRooms(status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable Long id) {
        return roomPresenter.presentRoomById(id);
    }

    /**
     * Checkout guest: room transitions from OCCUPIED to DIRTY, auto-generates cleaning task.
     */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<RoomResponse> checkOut(@PathVariable Long id) {
        return roomPresenter.presentCheckedOutRoom(id);
    }

    /**
     * Guest Check-in: strictly barriers allocation unless room is in READY status.
     */
    @PostMapping("/{id}/check-in")
    public ResponseEntity<RoomResponse> checkIn(@PathVariable Long id) {
        return roomPresenter.presentCheckedInRoom(id);
    }

    /**
     * Mark room READY: requires room to be in INSPECTED status with PASSED inspection.
     */
    @PostMapping("/{id}/ready")
    public ResponseEntity<RoomResponse> markReady(@PathVariable Long id) {
        return roomPresenter.presentReadyRoom(id);
    }

    /**
     * Sends room back to cleaning from CLEANED or INSPECTED status due to defect or failed check.
     */
    @PostMapping("/{id}/send-to-cleaning")
    public ResponseEntity<RoomResponse> sendBackToCleaning(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String supervisorName) {
        return roomPresenter.presentSentBackToCleaning(id, reason, supervisorName);
    }
}
