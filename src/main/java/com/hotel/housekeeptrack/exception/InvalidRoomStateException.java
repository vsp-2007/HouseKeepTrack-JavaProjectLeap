package com.hotel.housekeeptrack.exception;

/**
 * Thrown when an invalid room lifecycle transition is attempted.
 */
public class InvalidRoomStateException extends RuntimeException {
    public InvalidRoomStateException(String message) {
        super(message);
    }
}
