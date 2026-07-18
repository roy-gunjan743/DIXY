package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

import java.time.LocalDateTime;
import java.util.Objects;

public class JfrEventListener {

    private final EventCollector collector;

    // Was: private final EventCollector collector = new EventCollector();
    // Now takes the same instance AgentBootstrap/VirtualDeadlockAgent created,
    // so JFR events land in the same place as JVMTI events.
    public JfrEventListener(EventCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");
        stream.enable("jdk.ThreadEnd");
        stream.enable("jdk.JavaMonitorEnter");
        stream.enable("jdk.JavaMonitorWait");
        stream.enable("jdk.ThreadPark");
        stream.enable("jdk.VirtualThreadPinned");

        // 1. Thread Start
        stream.onEvent("jdk.ThreadStart", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_START);

            RecordedThread thread = event.getThread("thread");
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 2. Thread End
        stream.onEvent("jdk.ThreadEnd", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_END);

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 3. Thread Park (Virtual Thread specific blocking)
        stream.onEvent("jdk.ThreadPark", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_PARKED);

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address"));
            }
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 4. Java Monitor Enter (Mapped to your LOCK_ACQUIRE)
        stream.onEvent("jdk.JavaMonitorEnter", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.LOCK_ACQUIRE);

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address"));
            }

            // NEW — the thread that already held the monitor when this
            // thread had to wait. Without this, there was no way to add a
            // reverse edge, so a live deadlock could never close into a cycle.
            if (event.hasField("previousOwner")) {
                RecordedThread owner = event.getThread("previousOwner");
                if (owner != null) {
                    dto.setOwnerThreadId(owner.getJavaThreadId());
                    dto.setOwnerThreadName(owner.getJavaName());
                }
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);

            System.out.println("🚨 JFR CAUGHT LOCK WAIT: " + dto.getThreadName() + " waiting on lock " + dto.getLockAddress());
        });

        // 5. Java Monitor Wait (Mapped to your THREAD_BLOCKED)
        stream.onEvent("jdk.JavaMonitorWait", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_BLOCKED);

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address"));
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 6. Virtual Thread Pinned — the event that makes the unified
        // pinning+deadlock graph claim real.
        stream.onEvent("jdk.VirtualThreadPinned", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.PINNING);

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());

                // TODO: jdk.VirtualThreadPinned has no distinct carrier-thread
                // field as of this JDK build — approximating carrier identity
                // via the OS thread id of the pinned virtual thread's current
                // carrier. Verify against `jfr metadata` for your JDK build
                // and replace with a dedicated carrier field if one becomes
                // available in a later JDK.
                dto.setCarrierThreadId(thread.getOSThreadId());
                dto.setCarrierThreadName("carrier-" + thread.getOSThreadId());
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);

            System.out.println("⚠️ JFR CAUGHT VIRTUAL THREAD PINNING: "
                    + dto.getThreadName() + " pinned to carrier " + dto.getCarrierThreadName());
        });
    }
}