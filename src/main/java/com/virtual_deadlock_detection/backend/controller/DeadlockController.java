package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.service.DeadlockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deadlocks")
public class DeadlockController {

    private final DeadlockService deadlockService;

    public DeadlockController(DeadlockService deadlockService) {
        this.deadlockService = deadlockService;
    }

    @GetMapping
    public List<DeadlockReportEntity> getReports() {
        return deadlockService.getAllReports();
    }

    @PostMapping
    public DeadlockReportEntity createReport(@RequestBody DeadlockReportEntity report) {
        return deadlockService.saveReport(report);
    }
}