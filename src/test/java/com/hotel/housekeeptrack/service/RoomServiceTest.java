package com.hotel.housekeeptrack.service;

import com.hotel.housekeeptrack.dto.CreateRoomRequest;
import com.hotel.housekeeptrack.exception.BusinessRuleException;
import com.hotel.housekeeptrack.exception.InvalidRoomStateException;
import com.hotel.housekeeptrack.model.InspectionResult;
import com.hotel.housekeeptrack.model.Room;
import com.hotel.housekeeptrack.model.RoomStatus;
import com.hotel.housekeeptrack.model.TaskPriority;
import com.hotel.housekeeptrack.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private CleaningTaskService cleaningTaskService;

    @Mock
    private com.hotel.housekeeptrack.repository.InspectionRepository inspectionRepository;

    @InjectMocks
    private RoomService roomService;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("101", "DELUXE", RoomStatus.READY);
        room.setId(1L);
    }

    @Test
    @DisplayName("Create room successfully when room number is unique")
    void testCreateRoomSuccess() {
        CreateRoomRequest req = new CreateRoomRequest("101", "DELUXE");
        when(roomRepository.existsByRoomNumber("101")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        Room created = roomService.createRoom(req);
        assertNotNull(created);
        assertEquals("101", created.getRoomNumber());
        assertEquals(RoomStatus.READY, created.getStatus());
        assertNotNull(created.getReadyAt());
    }

    @Test
    @DisplayName("Create room throws exception when room number already exists")
    void testCreateRoomDuplicateFails() {
        CreateRoomRequest req = new CreateRoomRequest("101", "DELUXE");
        when(roomRepository.existsByRoomNumber("101")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> roomService.createRoom(req));
    }

    @Test
    @DisplayName("Check-in barrier: Check-in succeeds only when room is strictly READY")
    void testCheckInSuccessWhenReady() {
        room.setStatus(RoomStatus.READY);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        Room checkedIn = roomService.checkIn(1L);
        assertEquals(RoomStatus.OCCUPIED, checkedIn.getStatus());
    }

    @Test
    @DisplayName("Check-in barrier: Check-in fails with InvalidRoomStateException when room is DIRTY")
    void testCheckInFailsWhenDirty() {
        room.setStatus(RoomStatus.DIRTY);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        InvalidRoomStateException ex = assertThrows(InvalidRoomStateException.class, () -> roomService.checkIn(1L));
        assertTrue(ex.getMessage().contains("strictly READY"));
    }

    @Test
    @DisplayName("Check-in barrier: Check-in fails when room is IN_CLEANING, CLEANED, or INSPECTED")
    void testCheckInFailsWhenNotReady() {
        for (RoomStatus status : new RoomStatus[]{RoomStatus.IN_CLEANING, RoomStatus.CLEANED, RoomStatus.INSPECTED, RoomStatus.OCCUPIED}) {
            room.setStatus(status);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

            InvalidRoomStateException ex = assertThrows(InvalidRoomStateException.class, () -> roomService.checkIn(1L));
            assertTrue(ex.getMessage().contains("strictly READY"));
        }
    }

    @Test
    @DisplayName("Checkout succeeds when room is OCCUPIED and creates cleaning task")
    void testCheckoutSuccess() {
        room.setStatus(RoomStatus.OCCUPIED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        Room result = roomService.checkOut(1L);
        assertEquals(RoomStatus.DIRTY, result.getStatus());
        assertNotNull(result.getDirtyAt());
        assertNull(result.getReadyAt());

        verify(cleaningTaskService).createAndDispatchTask(eq(room), eq(TaskPriority.NORMAL), anyString());
    }

    @Test
    @DisplayName("Checkout fails when room is not OCCUPIED")
    void testCheckoutFailsIfNotOccupied() {
        room.setStatus(RoomStatus.READY);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        InvalidRoomStateException ex = assertThrows(InvalidRoomStateException.class, () -> roomService.checkOut(1L));
        assertTrue(ex.getMessage().contains("Room must be OCCUPIED"));
    }

    @Test
    @DisplayName("Mark ready succeeds when room is INSPECTED and inspection passed")
    void testMarkReadySuccess() {
        room.setStatus(RoomStatus.INSPECTED);
        room.setLastInspectionResult(InspectionResult.PASSED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        Room result = roomService.markReady(1L);
        assertEquals(RoomStatus.READY, result.getStatus());
        assertNotNull(result.getReadyAt());
    }

    @Test
    @DisplayName("Mark ready fails when room is not in INSPECTED status")
    void testMarkReadyFailsIfNotInspected() {
        room.setStatus(RoomStatus.CLEANED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThrows(InvalidRoomStateException.class, () -> roomService.markReady(1L));
    }

    @Test
    @DisplayName("Mark ready fails when room has not passed inspection")
    void testMarkReadyFailsIfNotPassed() {
        room.setStatus(RoomStatus.INSPECTED);
        room.setLastInspectionResult(InspectionResult.FAILED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThrows(BusinessRuleException.class, () -> roomService.markReady(1L));
    }

    @Test
    @DisplayName("Send back to cleaning succeeds from INSPECTED status")
    void testSendBackToCleaningFromInspected() {
        room.setStatus(RoomStatus.INSPECTED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        Room result = roomService.sendBackToCleaning(1L, "Missed bathroom mirror", "Supervisor Dave");
        assertEquals(RoomStatus.DIRTY, result.getStatus());
        assertEquals(InspectionResult.FAILED, result.getLastInspectionResult());
        assertNotNull(result.getDirtyAt());
        assertNull(result.getReadyAt());

        verify(inspectionRepository).save(any());
        verify(cleaningTaskService).createAndDispatchTask(eq(room), eq(TaskPriority.HIGH), anyString());
    }

    @Test
    @DisplayName("Send back to cleaning succeeds from CLEANED status")
    void testSendBackToCleaningFromCleaned() {
        room.setStatus(RoomStatus.CLEANED);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        Room result = roomService.sendBackToCleaning(1L, "Unsatisfactory cleaning", "Supervisor Dave");
        assertEquals(RoomStatus.DIRTY, result.getStatus());
        verify(cleaningTaskService).createAndDispatchTask(eq(room), eq(TaskPriority.HIGH), anyString());
    }

    @Test
    @DisplayName("Send back to cleaning fails when room is OCCUPIED, READY, or DIRTY")
    void testSendBackToCleaningFailsForInvalidStatus() {
        for (RoomStatus status : new RoomStatus[]{RoomStatus.OCCUPIED, RoomStatus.READY, RoomStatus.DIRTY}) {
            room.setStatus(status);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThrows(InvalidRoomStateException.class, () -> roomService.sendBackToCleaning(1L, "Reason", "Supervisor Dave"));
        }
    }
}
