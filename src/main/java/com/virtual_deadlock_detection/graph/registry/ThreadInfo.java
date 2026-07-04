package com.virtual_deadlock_detection.graph.registry;

import java.util.Objects;

public class ThreadInfo {

    private final long threadId;
    private final String threadName;
    private volatile Thread.State state;
    private final boolean virtualThread;

    public ThreadInfo(long threadId,
                      String threadName,
                      Thread.State state,
                      boolean virtualThread) {

        this.threadId = threadId;
        this.threadName = threadName;
        this.state = state;
        this.virtualThread = virtualThread;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public Thread.State getState() {
        return state;
    }

    public void setState(Thread.State state) {
        this.state = state;
    }

    public boolean isVirtualThread() {
        return virtualThread;
    }

    @Override
    public String toString() {
        return "ThreadInfo{" +
                "threadId=" + threadId +
                ", threadName='" + threadName + '\'' +
                ", state=" + state +
                ", virtualThread=" + virtualThread +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId);
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;

        if (!(obj instanceof ThreadInfo))
            return false;

        ThreadInfo other = (ThreadInfo) obj;

        return threadId == other.threadId;
    }

}