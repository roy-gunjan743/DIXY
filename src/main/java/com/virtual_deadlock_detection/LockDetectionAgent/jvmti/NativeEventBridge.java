package com.virtual_deadlock_detection.LockDetectionAgent.jvmti;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;

import java.time.LocalDateTime;

public class NativeEventBridge {

    private static final EventCollector collector =
            new EventCollector();

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

        EventDTO dto = new EventDTO();

        dto.setEventType(EventType.LOCK_OWNED);

        dto.setThreadId(waitingThreadId);
        dto.setThreadName(waitingThreadName);

        dto.setOwnerThreadId(ownerThreadId);
        dto.setOwnerThreadName(ownerThreadName);

        dto.setLockAddress(lockAddress);

        dto.setTimestamp(LocalDateTime.now());

        collector.collect(dto);

        System.out.println("--------------------------------");
        System.out.println("JVMTI -> Java");
        System.out.println("Waiting Thread : " + waitingThreadName);
        System.out.println("Owner Thread   : " + ownerThreadName);
        System.out.println("Lock Address   : " + lockAddress);
        System.out.println("--------------------------------");
    }
    public static void publishLockReleased(
            long threadId,
            long lockAddress,
            long timestamp) {

        EventDTO dto = new EventDTO();

        dto.setEventType(EventType.LOCK_RELEASE);

        dto.setThreadId(threadId);

        dto.setLockAddress(lockAddress);

        dto.setTimestamp(LocalDateTime.now());

        collector.collect(dto);

        System.out.println("--------------------------------");
        System.out.println("Lock Released");
        System.out.println("Thread : " + threadId);
        System.out.println("Lock   : " + lockAddress);
        System.out.println("--------------------------------");
    }
}