package com.virtual_deadlock_detection.LockDetectionAgent.monitor;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls ThreadMXBean on an interval and reports LIVE blocked-on-monitor
 * state directly into the shared EventCollector.
 *
 * WHY THIS EXISTS: jdk.JavaMonitorEnter is a JFR *duration* event — it only
 * commits once a thread successfully acquires the monitor it was contending
 * for. A permanently blocked thread (a real deadlock) never acquires it,
 * so the event never fires — confirmed in CI: two genuinely deadlocked
 * threads produced zero LOCK_ACQUIRE events for either side, for the
 * entire 3-second test window.
 *
 * ThreadMXBean.dumpAllThreads() instead reports live, in-progress blocking
 * state on every poll, independent of whether the block ever resolves.
 */
public class ThreadStatePoller {

    private final EventCollector collector;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vld-thread-state-poller");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean running = false;

    public ThreadStatePoller(EventCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    public void start(long intervalMillis) {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::pollOnce, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdownNow();
    }

    private void pollOnce() {
        try {
            ThreadInfo[] allThreads = threadMXBean.dumpAllThreads(true, true);

            for (ThreadInfo info : allThreads) {
                if (info == null) continue;
                if (info.getThreadState() != Thread.State.BLOCKED) continue;

                LockInfo lockInfo = info.getLockInfo();
                if (lockInfo == null) continue;

                long ownerId = info.getLockOwnerId();
                if (ownerId == -1) continue; // owner unknown — nothing to build an edge to

                EventDTO dto = new EventDTO();
                dto.setEventType(EventType.LOCK_ACQUIRE);
                dto.setThreadId(info.getThreadId());
                dto.setThreadName(info.getThreadName());
                dto.setLockAddress(lockInfo.getIdentityHashCode());
                dto.setOwnerThreadId(ownerId);
                dto.setOwnerThreadName(info.getLockOwnerName());
                dto.setTimestamp(LocalDateTime.now());

                collector.collect(dto);
            }
        } catch (Throwable t) {
            // Never let a polling hiccup kill the scheduler.
            System.err.println("ThreadStatePoller: poll failed: " + t);
        }
    }
}