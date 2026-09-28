package com.hotel.housekeeptrack.model;

/**
 * Enumeration of logged business actions across hotel lifecycle and staff management.
 */
public enum AuditAction {
    ROOM_CREATED,
    CHECK_IN,
    CHECKOUT,
    MARK_READY,
    SEND_TO_CLEANING,
    TASK_ASSIGNED,
    TASK_COMPLETED,
    INSPECTION_PASSED,
    INSPECTION_FAILED,
    STAFF_REGISTERED,
    STAFF_STATUS_CHANGED,
    REVERTED,
    ROOM_DELETED,
    STAFF_DELETED
}
