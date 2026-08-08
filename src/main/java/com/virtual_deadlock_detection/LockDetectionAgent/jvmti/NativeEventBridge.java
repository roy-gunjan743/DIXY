package com.virtual_deadlock_detection.LockDetectionAgent.jvmti;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class NativeEventBridge {

    private static final Logger log = LoggerFactory.getLogger(NativeEventBridge.class);
    private static final EventCollector collector = new EventCollector();
    private static final ThreadLocal<Boolean> IN_BRIDGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

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

        if (Boolean.TRUE.equals(IN_BRIDGE.get())) {
            return;
        }
        IN_BRIDGE.set(Boolean.TRUE);
        try {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.LOCK_OWNED);
            dto.setThreadId(waitingThreadId);
            dto.setThreadName(waitingThreadName);
            dto.setOwnerThreadId(ownerThreadId);
            dto.setOwnerThreadName(ownerThreadName);
            dto.setLockAddress(lockAddress);
            dto.setTimestamp(LocalDateTime.now());

            collector.collect(dto);
        } catch (Throwable t) {
            // Defensive: do not propagate errors back to JVMTI callback thread
        } finally {
            IN_BRIDGE.set(Boolean.FALSE);
        }
    }

    public static void publishLockReleased(
            long threadId,
            long lockAddress,
            long timestamp) {

        if (Boolean.TRUE.equals(IN_BRIDGE.get())) {
            return;
        }
        IN_BRIDGE.set(Boolean.TRUE);
        try {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.LOCK_RELEASE);
            dto.setThreadId(threadId);
            dto.setLockAddress(lockAddress);
            dto.setTimestamp(LocalDateTime.now());

            collector.collect(dto);
        } catch (Throwable t) {
            // Defensive: do not propagate errors back to JVMTI callback thread
        } finally {
            IN_BRIDGE.set(Boolean.FALSE);
        }
    }
}