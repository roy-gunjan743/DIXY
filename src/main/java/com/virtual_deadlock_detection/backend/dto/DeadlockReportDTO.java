package com.virtual_deadlock_detection.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeadlockReportDTO {

    private Long id;

    private String severity;

    private boolean resolved;

    private Long analysisTimeMicros;

    private LocalDateTime detectedAt;

    private List<String> threads;
}