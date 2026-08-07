package com.virtual_deadlock_detection.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentDeadlockDTO {

    private String severity;

    private Long analysisTimeMicros;

    private String appName;

    private String instanceId;

    private String detectionStrategy;

    private List<ThreadRef> threads;

    @Data
    public static class ThreadRef {
        private Long id;
        private String name;
    }
}