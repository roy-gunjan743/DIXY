package com.virtual_deadlock_detection.graph.registry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class LockRegistry {

    private final ConcurrentHashMap<Long, LockInfo> locks =
            new ConcurrentHashMap<>();

    /**
     * Thread acquired a monitor.
     */
    public void acquire(long monitorId,
                        long threadId,
                        String threadName) {

        locks.put(
                monitorId,
                new LockInfo(
                        monitorId,
                        threadId,
                        threadName
                )
        );
    }

    /**
     * Thread released a monitor.
     */
    public void release(long monitorId) {

        locks.remove(monitorId);

    }

    /**
     * Returns owner information.
     */
    public LockInfo get(long monitorId) {

        return locks.get(monitorId);

    }

    /**
     * Is this monitor owned?
     */
    public boolean contains(long monitorId) {

        return locks.containsKey(monitorId);

    }

    /**
     * All active locks.
     */
    public Collection<LockInfo> getAllLocks() {

        return locks.values();

    }

    public void clear() {

        locks.clear();

    }

}