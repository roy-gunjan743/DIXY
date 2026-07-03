package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.service.TelemetryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public String receiveTelemetry() {

        telemetryService.processTelemetry();

        return "Telemetry received successfully";
    }
}