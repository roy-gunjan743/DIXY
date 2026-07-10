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
            long ownerThreadId,
            String ownerThreadName,
            long lockAddress) {

        EventDTO dto = new EventDTO();

        dto.setEventType(EventType.LOCK_OWNED);

        dto.setOwnerThreadId(ownerThreadId);
        dto.setOwnerThreadName(ownerThreadName);

        dto.setLockAddress(lockAddress);

        dto.setTimestamp(LocalDateTime.now());

        collector.collect(dto);

        System.out.println("--------------------------------");
        System.out.println("JVMTI -> Java");
        System.out.println("Owner Thread : " + ownerThreadName);
        System.out.println("Lock Address : " + lockAddress);
        System.out.println("--------------------------------");
    }
}