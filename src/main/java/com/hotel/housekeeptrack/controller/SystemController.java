package com.hotel.housekeeptrack.controller;

import com.hotel.housekeeptrack.dto.RevertActionResponse;
import com.hotel.housekeeptrack.service.RevertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing system-level management actions including mistake-handling rollbacks.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final RevertService revertService;

    public SystemController(RevertService revertService) {
        this.revertService = revertService;
    }

    /**
     * Reverts the most recent revertible operational action.
     */
    @PostMapping("/revert-last")
    public ResponseEntity<RevertActionResponse> revertLastAction() {
        return ResponseEntity.ok(revertService.revertLastAction());
    }
}
