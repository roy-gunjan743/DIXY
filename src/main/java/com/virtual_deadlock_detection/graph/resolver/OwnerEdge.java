package com.virtual_deadlock_detection.graph.resolver;

public class OwnerEdge {

    private final long waitingThreadId;
    private final long ownerThreadId;

    public OwnerEdge(long waitingThreadId,
                     long ownerThreadId) {

        this.waitingThreadId = waitingThreadId;
        this.ownerThreadId = ownerThreadId;
    }

    public long getWaitingThreadId() {
        return waitingThreadId;
    }

    public long getOwnerThreadId() {
        return ownerThreadId;
    }

    @Override
    public String toString() {
        return waitingThreadId + " -> " + ownerThreadId;
    }
}