package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.dto.DeadlockReportDTO;
import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.service.DeadlockReportService;
import com.virtual_deadlock_detection.backend.service.DeadlockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deadlocks")
public class DeadlockController {

    private final DeadlockService deadlockService;
    private final DeadlockReportService deadlockReportService;

    public DeadlockController(DeadlockService deadlockService,
                              DeadlockReportService deadlockReportService) {
        this.deadlockService = deadlockService;
        this.deadlockReportService = deadlockReportService;
    }

    /**
     * Returns formatted deadlock reports for the dashboard.
     */
    @GetMapping
    public List<DeadlockReportDTO> getReports() {
        return deadlockReportService.getAllReports();
    }

    /**
     * Returns a single deadlock report.
     */
    @GetMapping("/{id}")
    public DeadlockReportDTO getReport(@PathVariable Long id) {
        return deadlockReportService.getReport(id);
    }

    /**
     * Manual report creation (mainly for testing).
     */
    @PostMapping
    public DeadlockReportEntity createReport(
            @RequestBody DeadlockReportEntity report) {

        return deadlockService.saveReport(report);
    }
}