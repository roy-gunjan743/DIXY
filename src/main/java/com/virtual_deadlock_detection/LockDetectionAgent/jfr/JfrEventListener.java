package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

import java.time.LocalDateTime;

public class JfrEventListener {

    private final EventCollector collector = new EventCollector();

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");
        stream.enable("jdk.ThreadEnd");
        stream.enable("jdk.JavaMonitorEnter");
        stream.enable("jdk.JavaMonitorWait");
        stream.enable("jdk.ThreadPark");

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
            dto.setEventType(EventType.LOCK_ACQUIRE); // MATCHES YOUR ENUM

            RecordedThread thread = event.getThread();
            if (thread != null) {
                dto.setThreadId(thread.getJavaThreadId());
                dto.setThreadName(thread.getJavaName());
            }

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address")); // REAL LOCK ADDRESS
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);

            System.out.println("🚨 JFR CAUGHT LOCK WAIT: " + dto.getThreadName() + " waiting on lock " + dto.getLockAddress());
        });

        // 5. Java Monitor Wait (Mapped to your THREAD_BLOCKED)
        stream.onEvent("jdk.JavaMonitorWait", event -> {
            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_BLOCKED); // MATCHES YOUR ENUM

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
    }
}