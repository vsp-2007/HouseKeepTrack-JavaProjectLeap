package com.hotel.housekeeptrack.presenter;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.dto.RoomResponse;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomPresenterTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomPresenter roomPresenter;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("401", "DELUXE", RoomStatus.READY);
        room.setId(401L);
    }

    @Test
    @DisplayName("MVP RoomPresenter: Presents created room with 201 CREATED status")
    void testPresentCreatedRoom() {
        CreateRoomRequest req = new CreateRoomRequest("401", "DELUXE");
        when(roomService.createRoom(any(CreateRoomRequest.class))).thenReturn(room);

        ResponseEntity<RoomResponse> response = roomPresenter.presentCreatedRoom(req);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("401", response.getBody().getRoomNumber());
    }

    @Test
    @DisplayName("MVP RoomPresenter: Presents rooms list with 200 OK")
    void testPresentRooms() {
        when(roomService.getAllRooms()).thenReturn(Collections.singletonList(room));

        ResponseEntity<List<RoomResponse>> response = roomPresenter.presentRooms(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
