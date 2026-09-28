package com.hotel.housekeeptrack.exception;

/**
 * Thrown when an invariant or business rule check fails.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
