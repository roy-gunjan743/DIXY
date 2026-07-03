package com.virtual_deadlock_detection.backend.dto;

import com.virtual_deadlock_detection.backend.enums.ThreadState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelemetryRequest {

    private Long threadId;

    private String threadName;

    private ThreadState state;

    private String carrierThread;

    private String lockId;

    private String lockType;
}