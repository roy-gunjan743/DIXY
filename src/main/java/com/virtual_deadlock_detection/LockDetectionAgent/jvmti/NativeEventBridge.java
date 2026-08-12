package com.virtual_deadlock_detection.LockDetectionAgent.jvmti;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class NativeEventBridge {

    private static final Logger log = LoggerFactory.getLogger(NativeEventBridge.class);
    private static final EventCollector collector =
            new EventCollector();

    // Small ring buffer so the CLI can show recent raw JVMTI callbacks
    // (see EventCommands "events list") without scrolling back through the
    // console. Capped at 200 entries; oldest is dropped once full.
    private static final int RING_CAPACITY = 200;
    private static final Deque<EventDTO> recentEvents = new ArrayDeque<>();

    // Reentrancy guard: handling a callback (remember()/collector.collect()/
    // log.debug()) itself acquires monitors. If the JVMTI hooks are active
    // for the current thread, that acquisition would fire this same
    // callback again recursively — main and JFR's own event-stream thread
    // can spiral into thousands of nested calls and crash. If we're already
    // inside a callback on this thread, drop the reentrant event instead of
    // processing it.
    private static final ThreadLocal<Boolean> IN_CALLBACK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static synchronized void remember(EventDTO dto) {
        recentEvents.addLast(dto);
        if (recentEvents.size() > RING_CAPACITY) {
            recentEvents.removeFirst();
        }
    }

    /** Returns the most recently captured JVMTI callback events, newest first. */
    public static synchronized List<EventDTO> getRecentEvents() {
        List<EventDTO> copy = new ArrayList<>(recentEvents);
        java.util.Collections.reverse(copy);
        return copy;
    }

    public static synchronized void clearRecentEvents() {
        recentEvents.clear();
    }

    /**
     * Called from JVMTI through JNI whenever
     * the native agent discovers that a thread owns a lock.
     */
    public static void publishLockOwned(
            long waitingThreadId,
            String waitingThreadName,
            long ownerThreadId,
            String ownerThreadName,
            long lockAddress,
            long timestamp) {

        if (Boolean.TRUE.equals(IN_CALLBACK.get())) {
            // We're already inside a callback on this thread — the work
            // below (remember/collector.collect/log.debug) just triggered
            // its own monitor event. Drop it rather than recursing.
            return;
        }

        IN_CALLBACK.set(Boolean.TRUE);
        try {
            EventDTO dto = new EventDTO();

            dto.setEventType(EventType.LOCK_OWNED);

            dto.setThreadId(waitingThreadId);
            dto.setThreadName(waitingThreadName);

            dto.setOwnerThreadId(ownerThreadId);
            dto.setOwnerThreadName(ownerThreadName);

            dto.setLockAddress(lockAddress);

            dto.setTimestamp(LocalDateTime.now());

            remember(dto);

            collector.collect(dto);

            log.debug("JVMTI -> Java: Waiting Thread: {}, Owner Thread: {}, Lock Address: {}", waitingThreadName, ownerThreadName, lockAddress);
        } finally {
            IN_CALLBACK.set(Boolean.FALSE);
        }
    }

    public static void publishLockReleased(
            long threadId,
            long lockAddress,
            long timestamp) {

        if (Boolean.TRUE.equals(IN_CALLBACK.get())) {
            return;
        }

        IN_CALLBACK.set(Boolean.TRUE);
        try {
            EventDTO dto = new EventDTO();

            dto.setEventType(EventType.LOCK_RELEASE);

            dto.setThreadId(threadId);

            dto.setLockAddress(lockAddress);

            dto.setTimestamp(LocalDateTime.now());

            remember(dto);

            collector.collect(dto);

            log.debug("Lock Released - Thread: {}, Lock: {}", threadId, lockAddress);
        } finally {
            IN_CALLBACK.set(Boolean.FALSE);
        }
    }
}