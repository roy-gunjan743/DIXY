package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.dto.TelemetryRequest;
import com.virtual_deadlock_detection.backend.dto.TelemetryResponse;
import com.virtual_deadlock_detection.backend.service.TelemetryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public TelemetryResponse receiveTelemetry(
            @RequestBody TelemetryRequest request) {

        return telemetryService.processTelemetry(request);
    }
}