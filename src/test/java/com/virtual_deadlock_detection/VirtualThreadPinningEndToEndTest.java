package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.monitor.ThreadStatePoller;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadPinningEndToEndTest {

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
    void detectsVirtualThreadPinningThroughRealPipeline() throws InterruptedException {

        Object monitor = new Object();

        Thread pinned = Thread.ofVirtual().name("pinning-vthread").start(() -> {
            synchronized (monitor) {
                sleepQuietly(2000);
            }
        });

        pinned.join();
        Thread.sleep(1000);

        WaitForGraph graph = RuntimeContext.GRAPH;

        boolean sawPinsEdge = graph.getEdges().values().stream()
                .flatMap(Set::stream)
                .anyMatch(edge -> edge.getType() == EdgeType.PINS);

        assertTrue(sawPinsEdge,
                "Expected a real PINS edge from the actual jdk.VirtualThreadPinned event. Graph edges: "
                        + graph.getEdges());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}