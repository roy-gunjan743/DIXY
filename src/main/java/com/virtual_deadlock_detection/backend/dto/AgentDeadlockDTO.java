package com.virtual_deadlock_detection.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentDeadlockDTO {

    private String severity;

    private Long analysisTimeMicros;

    private List<Long> threadIds;
}