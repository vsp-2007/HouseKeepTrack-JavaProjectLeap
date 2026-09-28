package com.hotel.housekeeptrack.model;

/**
 * Lifecycle states of a hotel room:
 * OCCUPIED -> DIRTY -> IN_CLEANING -> CLEANED -> INSPECTED -> READY -> OCCUPIED.
 * Regression path: INSPECTED -> DIRTY on inspection failure.
 */
public enum RoomStatus {
    DIRTY,
    IN_CLEANING,
    CLEANED,
    INSPECTED,
    READY,
    OCCUPIED
}
