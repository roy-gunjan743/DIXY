package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Objects;

public class JfrEventListener {

    private static final Logger log = LoggerFactory.getLogger(JfrEventListener.class);
    private final EventCollector collector;

    public JfrEventListener(EventCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    private boolean isIgnoredThread(String threadName) {
        if (threadName == null)
            return false;
        String name = threadName.toLowerCase();
        return name.equals("main") ||
                name.contains("jfr event stream") ||
                name.contains("jfr periodic") ||
                name.contains("catalina-utility") ||
                name.contains("vld-thread-state-poller") ||
                name.contains("lettuce-timer") ||
                name.contains("common-cleaner") ||
                name.contains("reference handler") ||
                name.contains("finalizer") ||
                name.contains("signal dispatcher") ||
                name.contains("attach listener") ||
                name.contains("notification thread") ||
                name.contains("compilerthread") ||
                name.contains("sweeper") ||
                name.contains("service thread") ||
                name.contains("vm thread") ||
                name.contains("http-nio") ||
                name.contains("tomcat") ||
                name.contains("httpclient") ||
                name.contains("virtualthread-unparker");
    }

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");
        stream.enable("jdk.ThreadEnd");
        stream.enable("jdk.JavaMonitorEnter");
        stream.enable("jdk.JavaMonitorWait");
        stream.enable("jdk.ThreadPark");
        stream.enable("jdk.VirtualThreadPinned").withoutThreshold();

        // 1. Thread Start
        stream.onEvent("jdk.ThreadStart", event -> {
            RecordedThread thread = event.getThread("thread");
            if (thread == null || isIgnoredThread(thread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_START);
            dto.setThreadId(thread.getJavaThreadId());
            dto.setThreadName(thread.getJavaName());
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 2. Thread End
        stream.onEvent("jdk.ThreadEnd", event -> {
            RecordedThread thread = event.getThread();
            if (thread == null || isIgnoredThread(thread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_END);
            dto.setThreadId(thread.getJavaThreadId());
            dto.setThreadName(thread.getJavaName());
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 3. Thread Park (Virtual Thread specific blocking)
        stream.onEvent("jdk.ThreadPark", event -> {
            RecordedThread thread = event.getThread();
            if (thread == null || isIgnoredThread(thread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_PARKED);
            dto.setThreadId(thread.getJavaThreadId());
            dto.setThreadName(thread.getJavaName());
            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 4. Java Monitor Enter (Lock Contention)
        stream.onEvent("jdk.JavaMonitorEnter", event -> {
            RecordedThread thread = event.getThread();
            if (thread == null || isIgnoredThread(thread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.LOCK_ACQUIRE);
            dto.setThreadId(thread.getJavaThreadId());
            dto.setThreadName(thread.getJavaName());

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address"));
            }

            if (event.hasField("previousOwner")) {
                RecordedThread owner = event.getThread("previousOwner");
                if (owner != null) {
                    dto.setOwnerThreadId(owner.getJavaThreadId());
                    dto.setOwnerThreadName(owner.getJavaName());
                }
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);

            log.debug("JFR Lock Wait: {} waiting on lock {}", dto.getThreadName(), dto.getLockAddress());
        });

        // 5. Java Monitor Wait
        stream.onEvent("jdk.JavaMonitorWait", event -> {
            RecordedThread thread = event.getThread();
            if (thread == null || isIgnoredThread(thread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.THREAD_BLOCKED);
            dto.setThreadId(thread.getJavaThreadId());
            dto.setThreadName(thread.getJavaName());

            if (event.hasField("address")) {
                dto.setLockAddress(event.getLong("address"));
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);
        });

        // 6. Virtual Thread Pinned
        stream.onEvent("jdk.VirtualThreadPinned", event -> {
            RecordedThread pinnedThread = null;
            if (event.hasField("pinnedThread")) {
                pinnedThread = event.getThread("pinnedThread");
            }
            if (pinnedThread == null) {
                pinnedThread = event.getThread();
            }

            RecordedThread carrierThread = null;
            if (event.hasField("carrierThread")) {
                carrierThread = event.getThread("carrierThread");
            }

            if (pinnedThread != null && isIgnoredThread(pinnedThread.getJavaName()))
                return;

            EventDTO dto = new EventDTO();
            dto.setEventType(EventType.PINNING);

            if (pinnedThread != null) {
                dto.setThreadId(pinnedThread.getJavaThreadId());
                dto.setThreadName(pinnedThread.getJavaName() != null ? pinnedThread.getJavaName()
                        : "vthread-" + pinnedThread.getJavaThreadId());
            } else {
                dto.setThreadId(Thread.currentThread().threadId());
                dto.setThreadName(Thread.currentThread().getName());
            }

            if (carrierThread != null) {
                dto.setCarrierThreadId(carrierThread.getJavaThreadId());
                dto.setCarrierThreadName(carrierThread.getJavaName() != null ? carrierThread.getJavaName()
                        : "carrier-" + carrierThread.getJavaThreadId());
            } else if (pinnedThread != null && pinnedThread.getOSThreadId() > 0) {
                dto.setCarrierThreadId(pinnedThread.getOSThreadId());
                dto.setCarrierThreadName("carrier-" + pinnedThread.getOSThreadId());
            } else {
                dto.setCarrierThreadId(0L);
                dto.setCarrierThreadName("carrier-unknown");
            }

            dto.setTimestamp(LocalDateTime.now());
            collector.collect(dto);

            log.info("Virtual thread pinning detected: {} pinned to carrier {}", dto.getThreadName(),
                    dto.getCarrierThreadName());
        });
    }
}