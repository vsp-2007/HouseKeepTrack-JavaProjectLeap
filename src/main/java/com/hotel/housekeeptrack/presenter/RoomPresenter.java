package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.dto.RoomResponse;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.service.RoomService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Presenter component in Model-View-Presenter (MVP) architecture.
 * Mediates between Room Model (Service/Repository) and View (RoomResponse).
 */
@Component
public class RoomPresenter {

    private final RoomService roomService;

    public RoomPresenter(RoomService roomService) {
        this.roomService = roomService;
    }

    public ResponseEntity<RoomResponse> presentCreatedRoom(CreateRoomRequest request) {
        Room room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<List<RoomResponse>> presentRooms(RoomStatus status) {
        List<Room> rooms = (status != null) ? roomService.getRoomsByStatus(status) : roomService.getAllRooms();
        List<RoomResponse> responses = rooms.stream().map(RoomResponse::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    public ResponseEntity<Page<RoomResponse>> presentRoomsPage(RoomStatus status, Pageable pageable) {
        Page<Room> rooms = roomService.getRoomsPaginated(status, pageable);
        Page<RoomResponse> responses = rooms.map(RoomResponse::fromEntity);
        return ResponseEntity.ok(responses);
    }

    public ResponseEntity<RoomResponse> presentRoomById(Long id) {
        Room room = roomService.getRoomById(id);
        return ResponseEntity.ok(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<RoomResponse> presentCheckedOutRoom(Long id) {
        Room room = roomService.checkOut(id);
        return ResponseEntity.ok(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<RoomResponse> presentCheckedInRoom(Long id) {
        Room room = roomService.checkIn(id);
        return ResponseEntity.ok(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<RoomResponse> presentReadyRoom(Long id) {
        Room room = roomService.markReady(id);
        return ResponseEntity.ok(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<RoomResponse> presentSentBackToCleaning(Long id, String reason, String supervisorName) {
        Room room = roomService.sendBackToCleaning(id, reason, supervisorName);
        return ResponseEntity.ok(RoomResponse.fromEntity(room));
    }

    public ResponseEntity<Void> presentDeletedRoom(Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}
