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
 * Runs alone in its own forked JVM (see build.gradle: forkEvery = 1) — the
 * deadlock created here is real and permanent.
 *
 * DESIGN NOTE (why this doesn't try to make the pinned thread part of the
 * deadlock's own lock chain): a virtual thread's own permanent block is not
 * observable by EITHER of the two mechanisms this pipeline has —
 * ThreadStatePoller only enumerates platform threads (virtual threads
 * aren't tracked via any ThreadGroup, by Loom's design), and JFR's
 * jdk.JavaMonitorEnter only commits on successful acquisition (never fires
 * for a block that never resolves — see ThreadStatePoller's own javadoc for
 * why it exists at all). So instead this test proves the actual claim
 * DeadlockDetector.correlatePinning() makes: a real, concurrent, platform-
 * thread deadlock (fully observable) correlated by TIME with a real,
 * concurrent virtual-thread pin (fully observable via JFR) — not a
 * structural graph position, which the JDK doesn't let us observe reliably
 * for virtual threads either way.
 */
class HybridPinningDeadlockEndToEndTest {

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
    void detectsHybridCycleWithPinningCorrelation() throws InterruptedException {

        // --- Real, permanent, platform-thread deadlock (fully observable) ---
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
                    // unreachable — real, permanent deadlock
                }
            }
        });

        Thread.ofPlatform().name("deadlock-thread-B").start(() -> {
            synchronized (lockB) {
                bHoldsLockB.countDown();
                awaitQuietly(aHoldsLockA);
                sleepQuietly(300);
                synchronized (lockA) {
                    // unreachable — real, permanent deadlock
                }
            }
        });

        // --- Real, concurrent virtual thread pin (fully observable via JFR) ---
        Object pinMonitor = new Object();

        Thread.ofVirtual().name("concurrent-pinner").start(() -> {
            synchronized (pinMonitor) {
                sleepQuietly(2000);
            }
        });

        // Give both the deadlock and the pin time to fully register.
        Thread.sleep(4000);

        WaitForGraph graph = RuntimeContext.GRAPH;
        DeadlockDetector detector = RuntimeContext.DETECTOR;

        List<DeadlockCycle> cycles = detector.detect(graph);

        assertFalse(cycles.isEmpty(),
                "Expected the real pipeline to detect the live deadlock. Graph edges: " + graph.getEdges());

        assertTrue(cycles.stream().anyMatch(DeadlockCycle::hasPinningCorrelation),
                "Expected at least one cycle to be correlated with a co-resident, real pinning event. Cycles: "
                        + cycles);
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