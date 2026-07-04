package com.virtual_deadlock_detection.graph.registry;

import java.time.Instant;

public class WaitingInfo {

    private final long threadId;

    private final String threadName;

    private final long monitorId;

    private final Instant waitingSince;

    public WaitingInfo(long threadId,
                       String threadName,
                       long monitorId) {

        this.threadId = threadId;
        this.threadName = threadName;
        this.monitorId = monitorId;
        this.waitingSince = Instant.now();
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getMonitorId() {
        return monitorId;
    }

    public Instant getWaitingSince() {
        return waitingSince;
    }

    @Override
    public String toString() {
        return "WaitingInfo{" +
                "threadId=" + threadId +
                ", threadName='" + threadName + '\'' +
                ", monitorId=" + monitorId +
                ", waitingSince=" + waitingSince +
                '}';
    }

}