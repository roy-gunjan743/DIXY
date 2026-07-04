package com.virtual_deadlock_detection.graph.registry;

import java.time.Instant;

public class LockInfo {

    private final long monitorId;

    private volatile long ownerThreadId;

    private volatile String ownerThreadName;

    private volatile Instant acquiredAt;

    public LockInfo(long monitorId,
                    long ownerThreadId,
                    String ownerThreadName) {

        this.monitorId = monitorId;
        this.ownerThreadId = ownerThreadId;
        this.ownerThreadName = ownerThreadName;
        this.acquiredAt = Instant.now();
    }

    public long getMonitorId() {
        return monitorId;
    }

    public long getOwnerThreadId() {
        return ownerThreadId;
    }

    public String getOwnerThreadName() {
        return ownerThreadName;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public void setOwner(long ownerThreadId,
                         String ownerThreadName) {

        this.ownerThreadId = ownerThreadId;
        this.ownerThreadName = ownerThreadName;
        this.acquiredAt = Instant.now();
    }

    @Override
    public String toString() {
        return "LockInfo{" +
                "monitorId=" + monitorId +
                ", ownerThreadId=" + ownerThreadId +
                ", ownerThreadName='" + ownerThreadName + '\'' +
                '}';
    }

}