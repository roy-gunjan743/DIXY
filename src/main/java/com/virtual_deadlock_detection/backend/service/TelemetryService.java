package com.virtual_deadlock_detection.backend.service;

import org.springframework.stereotype.Service;

@Service
public class TelemetryService {

    public void processTelemetry() {
        System.out.println("Telemetry processing started...");
    }
}