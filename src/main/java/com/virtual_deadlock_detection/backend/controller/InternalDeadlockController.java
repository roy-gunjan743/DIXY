package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.dto.AgentDeadlockDTO;
import com.virtual_deadlock_detection.backend.service.AgentDeadlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal")
public class InternalDeadlockController {

    private final AgentDeadlockService service;

    public InternalDeadlockController(AgentDeadlockService service) {
        this.service = service;
    }

    @PostMapping("/deadlocks")
    public ResponseEntity<String> receiveDeadlock(
            @RequestBody AgentDeadlockDTO dto) {

        service.save(dto);

        return ResponseEntity.ok("Deadlock Stored");
    }
}