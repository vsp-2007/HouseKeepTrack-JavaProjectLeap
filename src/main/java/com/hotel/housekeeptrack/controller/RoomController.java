package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.dto.RoomResponse;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.presenter.RoomPresenter;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public ResponseEntity<?> getAllRooms(
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (page != null) {
            int pageSize = (size != null && size > 0) ? size : 5;
            Sort sorting = Sort.by("id").ascending();
            if (sort != null && !sort.isBlank()) {
                String[] parts = sort.split(",");
                if (parts.length > 1) {
                    Sort.Direction dir = parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
                    sorting = Sort.by(dir, parts[0].trim());
                } else {
                    sorting = Sort.by(parts[0].trim());
                }
            }
            Pageable pageable = PageRequest.of(page, pageSize, sorting);
            return roomPresenter.presentRoomsPage(status, pageable);
        }
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
