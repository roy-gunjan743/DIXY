package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.monitor.ThreadStatePoller;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs in its own forked JVM (see build.gradle: forkEvery = 1) because the
 * deadlock this test creates is real and permanent — the participating
 * threads never terminate, so they must not leak into any other test.
 */
class ClassicDeadlockEndToEndTest {

    private RecordingManager recordingManager;
    private ThreadStatePoller poller;

    @BeforeEach
    void startAgentPipeline() {
        RuntimeContext.GRAPH.rebuild(Collections.emptySet());

        EventCollector collector = new EventCollector();

        recordingManager = new RecordingManager();
        recordingManager.setCollector(collector);
        recordingManager.startRecording();

        poller = new ThreadStatePoller(collector);
        poller.start(250);
    }

    @AfterEach
    void stopAgentPipeline() {
        if (recordingManager != null) recordingManager.stopRecording();
        if (poller != null) poller.stop();
    }

    @Test
    void detectsClassicDeadlockThroughRealPipeline() throws InterruptedException {

        Object lockA = new Object();
        Object lockB = new Object();

        CountDownLatch aHoldsLockA = new CountDownLatch(1);
        CountDownLatch bHoldsLockB = new CountDownLatch(1);

        Thread.ofPlatform().name("deadlock-thread-A").start(() -> {
            synchronized (lockA) {
                aHoldsLockA.countDown();
                awaitQuietly(bHoldsLockB);
                sleepQuietly(300);
                synchronized (lockB) {
                    // unreachable — this is a real, permanent deadlock
                }
            }
        });

        Thread.ofPlatform().name("deadlock-thread-B").start(() -> {
            synchronized (lockB) {
                bHoldsLockB.countDown();
                awaitQuietly(aHoldsLockA);
                sleepQuietly(300);
                synchronized (lockA) {
                    // unreachable — this is a real, permanent deadlock
                }
            }
        });

        Thread.sleep(3000);

        WaitForGraph graph = RuntimeContext.GRAPH;
        DeadlockDetector detector = RuntimeContext.DETECTOR;

        List<DeadlockCycle> cycles = detector.detect(graph);

        assertFalse(cycles.isEmpty(),
                "Expected the real pipeline to detect the live classic deadlock. Graph edges: "
                        + graph.getEdges());

        assertTrue(cycles.stream().noneMatch(DeadlockCycle::isPinningCycle),
                "This cycle should be a pure lock deadlock, not a pinning cycle");

        // No cleanup possible — these threads are genuinely, permanently
        // deadlocked. That's fine: the JVM this test runs in ends here.
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}