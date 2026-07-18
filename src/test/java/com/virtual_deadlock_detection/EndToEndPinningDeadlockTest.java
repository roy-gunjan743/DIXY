package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real end-to-end test: actual virtual threads pin, actual threads deadlock,
 * both run through the real production pipeline — RecordingManager -> JfrRecorder
 * -> JfrEventListener -> EventCollector -> EventSender -> GraphGateway ->
 * RuntimeContext.GRAPH -> DeadlockDetector — NOT manual graph.addEdge() calls
 * (see the old GraphEngineTest.java for what NOT to repeat).
 *
 * This is Phase 0 / Step 2 of the roadmap: reduction-to-practice evidence for
 * the unified pinning+deadlock correlation claim.
 */
class EndToEndPinningDeadlockTest {

    private RecordingManager recordingManager;

    @BeforeEach
    void startAgentPipeline() {
        // RuntimeContext.GRAPH is a shared static — clear it so edges from a
        // previous test (or previous run) don't leak into this one's assertions.
        RuntimeContext.GRAPH.rebuild(Collections.emptySet());

        EventCollector collector = new EventCollector();
        recordingManager = new RecordingManager();
        recordingManager.setCollector(collector);
        recordingManager.startRecording();
    }

    @AfterEach
    void stopAgentPipeline() {
        if (recordingManager != null) {
            recordingManager.stopRecording();
        }
    }

    @Test
    void detectsClassicDeadlockThroughRealPipeline() throws InterruptedException {

        Object lockA = new Object();
        Object lockB = new Object();

        CountDownLatch aHoldsLockA = new CountDownLatch(1);
        CountDownLatch bHoldsLockB = new CountDownLatch(1);

        Thread threadA = Thread.ofPlatform().name("deadlock-thread-A").start(() -> {
            synchronized (lockA) {
                aHoldsLockA.countDown();
                awaitQuietly(bHoldsLockB);
                sleepQuietly(300); // give JFR time to see contention before B tries lockA
                synchronized (lockB) {
                    // unreachable while deadlocked
                }
            }
        });

        Thread threadB = Thread.ofPlatform().name("deadlock-thread-B").start(() -> {
            synchronized (lockB) {
                bHoldsLockB.countDown();
                awaitQuietly(aHoldsLockA);
                sleepQuietly(300);
                synchronized (lockA) {
                    // unreachable while deadlocked
                }
            }
        });

        // Give the real deadlock time to form and for JFR to emit contention events.
        Thread.sleep(3000);

        WaitForGraph graph = RuntimeContext.GRAPH;
        DeadlockDetector detector = RuntimeContext.DETECTOR;

        List<DeadlockCycle> cycles = detector.detect(graph);

        assertFalse(cycles.isEmpty(),
                "Expected the real pipeline to detect the live classic deadlock. Graph edges: "
                        + graph.getEdges());

        assertTrue(cycles.stream().noneMatch(DeadlockCycle::isPinningCycle),
                "This cycle should be a pure lock deadlock, not a pinning cycle");

        threadA.interrupt();
        threadB.interrupt();
    }

    @Test
    void detectsVirtualThreadPinningThroughRealPipeline() throws InterruptedException {

        Object monitor = new Object();

        Thread pinned = Thread.ofVirtual().name("pinning-vthread").start(() -> {
            synchronized (monitor) {
                // Sleeping while holding a monitor on a virtual thread pins the
                // carrier on JDK 21 (pre-JEP 491) — a real trigger, not simulated.
                sleepQuietly(2000);
            }
        });

        pinned.join();
        Thread.sleep(1000); // let JFR's async stream flush jdk.VirtualThreadPinned

        WaitForGraph graph = RuntimeContext.GRAPH;

        boolean sawPinsEdge = graph.getEdges().values().stream()
                .flatMap(Set::stream)
                .anyMatch(edge -> edge.getType() == EdgeType.PINS);

        assertTrue(sawPinsEdge,
                "Expected a real PINS edge from the actual jdk.VirtualThreadPinned event. Graph edges: "
                        + graph.getEdges());
    }

    @Test
    void bothEdgeTypesCoexistInOneLiveScenario() throws InterruptedException {

        // Partial evidence toward the core patent claim: a PINS edge and a
        // WAITS_FOR edge produced by the SAME live scenario, landing in the
        // SAME graph, visible to the SAME detector pass.
        //
        // NOTE: this does not yet assert a single cycle spanning both edge
        // types. That requires modeling which other work is blocked on the
        // starved carrier specifically (a carrier-scheduling edge that
        // doesn't exist in the graph model yet) — flagged as a follow-up,
        // not asserted here to avoid a false-positive test.

        Object monitorX = new Object();
        CountDownLatch pinnerHoldsMonitor = new CountDownLatch(1);

        Thread vThreadPinner = Thread.ofVirtual().name("hybrid-vthread-pinner").start(() -> {
            synchronized (monitorX) {
                pinnerHoldsMonitor.countDown();
                sleepQuietly(2500); // pins the carrier while holding monitorX
            }
        });

        awaitQuietly(pinnerHoldsMonitor);
        Thread.sleep(200);

        Thread platformWaiter = Thread.ofPlatform().name("hybrid-platform-waiter").start(() -> {
            synchronized (monitorX) {
                // reachable only after vThreadPinner releases monitorX
            }
        });

        Thread.sleep(3000);
        platformWaiter.join(1000);

        WaitForGraph graph = RuntimeContext.GRAPH;

        boolean sawPins = graph.getEdges().values().stream()
                .flatMap(Set::stream)
                .anyMatch(e -> e.getType() == EdgeType.PINS);

        boolean sawWaitsFor = graph.getEdges().values().stream()
                .flatMap(Set::stream)
                .anyMatch(e -> e.getType() == EdgeType.WAITS_FOR);

        assertTrue(sawPins, "Expected a real PINS edge in this scenario. Graph edges: " + graph.getEdges());
        assertTrue(sawWaitsFor, "Expected a real WAITS_FOR edge in this scenario. Graph edges: " + graph.getEdges());
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