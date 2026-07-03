package com.virtual_deadlock_detection.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TelemetryResponse {

    private boolean success;

    private String message;
}