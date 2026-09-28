package com.hotel.housekeeptrack.exception;

import com.hotel.housekeeptrack.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
    }

    @Test
    @DisplayName("Handle ResourceNotFoundException returns 404")
    void testHandleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Item not found");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Item not found", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    @DisplayName("Handle InvalidRoomStateException returns 400")
    void testHandleInvalidRoomState() {
        InvalidRoomStateException ex = new InvalidRoomStateException("Cannot checkout dirty room");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleInvalidRoomState(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Room State Transition", response.getBody().getError());
        assertEquals("Cannot checkout dirty room", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Handle BusinessRuleException returns 400")
    void testHandleBusinessRule() {
        BusinessRuleException ex = new BusinessRuleException("Duplicate entity");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleBusinessRule(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Business Rule Violation", response.getBody().getError());
        assertEquals("Duplicate entity", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Handle IllegalArgumentException returns 400")
    void testHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad Request", response.getBody().getError());
    }

    @Test
    @DisplayName("Handle OptimisticLockingFailureException returns 409 Conflict")
    void testHandleOptimisticLocking() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Version conflict");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleOptimisticLocking(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Concurrent Modification Conflict", response.getBody().getError());
    }

    @Test
    @DisplayName("Handle generic Exception returns 500")
    void testHandleGeneralException() {
        Exception ex = new RuntimeException("Unexpected error");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getError());
    }
}
