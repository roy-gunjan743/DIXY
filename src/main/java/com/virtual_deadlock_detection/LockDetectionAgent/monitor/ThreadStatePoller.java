package com.virtual_deadlock_detection.LockDetectionAgent.monitor;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls ThreadMXBean on an interval and reports LIVE blocked-on-monitor
 * state directly into the shared EventCollector.
 */
public class ThreadStatePoller {

    private static final Logger log = LoggerFactory.getLogger(ThreadStatePoller.class);
    private final EventCollector collector;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final Map<Long, Integer> activeBlockedLocks = new ConcurrentHashMap<>();
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

    private boolean isIgnoredThread(String threadName) {
        if (threadName == null) return false;
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
            Set<Long> currentlyBlockedThreads = ConcurrentHashMap.newKeySet();

            for (ThreadInfo info : allThreads) {
                if (info == null) continue;
                if (info.getThreadState() != Thread.State.BLOCKED) continue;
                if (isIgnoredThread(info.getThreadName())) continue;

                LockInfo lockInfo = info.getLockInfo();
                if (lockInfo == null) continue;

                long ownerId = info.getLockOwnerId();
                if (ownerId == -1) continue; // owner unknown — nothing to build an edge to

                long threadId = info.getThreadId();
                int lockHash = lockInfo.getIdentityHashCode();
                currentlyBlockedThreads.add(threadId);

                // Deduplicate: only fire event if this is a newly observed block or target lock changed
                Integer previousLock = activeBlockedLocks.get(threadId);
                if (previousLock != null && previousLock == lockHash) {
                    continue;
                }

                activeBlockedLocks.put(threadId, lockHash);

                EventDTO dto = new EventDTO();
                dto.setEventType(EventType.THREAD_BLOCKED);
                dto.setThreadId(threadId);
                dto.setThreadName(info.getThreadName());
                dto.setLockAddress(lockHash);
                dto.setOwnerThreadId(ownerId);
                dto.setOwnerThreadName(info.getLockOwnerName());
                dto.setTimestamp(LocalDateTime.now());

                collector.collect(dto);
            }

            // Cleanup threads that are no longer blocked
            activeBlockedLocks.keySet().removeIf(tid -> !currentlyBlockedThreads.contains(tid));

        } catch (Throwable t) {
            log.debug("ThreadStatePoller: poll failed: {}", t.getMessage());
        }
    }
}