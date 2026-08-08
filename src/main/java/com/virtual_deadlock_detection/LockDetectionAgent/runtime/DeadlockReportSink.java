package com.virtual_deadlock_detection.LockDetectionAgent.runtime;

import com.virtual_deadlock_detection.graph.model.DeadlockCycle;

@FunctionalInterface
public interface DeadlockReportSink {
    void report(DeadlockCycle cycle);
}