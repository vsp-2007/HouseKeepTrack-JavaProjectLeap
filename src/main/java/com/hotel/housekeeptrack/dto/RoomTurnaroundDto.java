package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.RoomStatus;

import java.time.LocalDateTime;

public class RoomTurnaroundDto {
    private Long roomId;
    private String roomNumber;
    private String roomType;
    private RoomStatus status;
    private LocalDateTime dirtyAt;
    private LocalDateTime readyAt;
    private Double turnaroundMinutes;

    public RoomTurnaroundDto() {
    }

    public RoomTurnaroundDto(Long roomId, String roomNumber, String roomType, RoomStatus status,
                             LocalDateTime dirtyAt, LocalDateTime readyAt, Double turnaroundMinutes) {
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.status = status;
        this.dirtyAt = dirtyAt;
        this.readyAt = readyAt;
        this.turnaroundMinutes = turnaroundMinutes;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public LocalDateTime getDirtyAt() {
        return dirtyAt;
    }

    public void setDirtyAt(LocalDateTime dirtyAt) {
        this.dirtyAt = dirtyAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public Double getTurnaroundMinutes() {
        return turnaroundMinutes;
    }

    public void setTurnaroundMinutes(Double turnaroundMinutes) {
        this.turnaroundMinutes = turnaroundMinutes;
    }
}
