package com.virtual_deadlock_detection.graph.registry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class WaitingRegistry {

    private final ConcurrentHashMap<Long, WaitingInfo> waitingThreads =
            new ConcurrentHashMap<>();

    /**
     * Thread starts waiting.
     */
    public void startWaiting(long threadId,
                             String threadName,
                             long monitorId) {

        waitingThreads.put(
                threadId,
                new WaitingInfo(
                        threadId,
                        threadName,
                        monitorId
                )
        );
    }

    /**
     * Thread acquired the monitor.
     */
    public void stopWaiting(long threadId) {

        waitingThreads.remove(threadId);

    }

    /**
     * Returns waiting information.
     */
    public WaitingInfo get(long threadId) {

        return waitingThreads.get(threadId);

    }

    /**
     * Is thread waiting?
     */
    public boolean isWaiting(long threadId) {

        return waitingThreads.containsKey(threadId);

    }

    /**
     * All waiting threads.
     */
    public Collection<WaitingInfo> getAllWaitingThreads() {

        return waitingThreads.values();

    }

    public void clear() {

        waitingThreads.clear();

    }

}