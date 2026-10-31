package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinningCorrelationTest {

    @Test
    void doesNotAttachPinningEvidenceObservedBeforeCycleFormed() throws InterruptedException {
        WaitForGraph graph = new WaitForGraph();
        GraphNode pinnedThread = new GraphNode(1, NodeType.VIRTUAL_THREAD, "unrelated-pinner");
        GraphNode carrier = new GraphNode(2, NodeType.CARRIER_THREAD, "carrier");
        graph.addEdge(new GraphEdge(pinnedThread, carrier, EdgeType.PINS));

        Thread.sleep(550);
        addDeadlockCycle(graph);

        List<DeadlockCycle> cycles = new DeadlockDetector().detect(graph);

        assertFalse(cycles.isEmpty(), "Expected the independent lock cycle to be detected");
        assertFalse(cycles.get(0).hasPinningCorrelation(),
                "A pinning event observed before this cycle formed must not be attached to it");
    }

    @Test
    void correlatesPinningObservedAfterCycleWithoutRequiringSharedNodes() throws InterruptedException {
        WaitForGraph graph = new WaitForGraph();
        addDeadlockCycle(graph);

        GraphNode pinnedThread = new GraphNode(1, NodeType.VIRTUAL_THREAD, "unrelated-pinner");
        GraphNode carrier = new GraphNode(2, NodeType.CARRIER_THREAD, "carrier");
        graph.addEdge(new GraphEdge(pinnedThread, carrier, EdgeType.PINS));

        Thread.sleep(550);

        List<DeadlockCycle> cycles = new DeadlockDetector().detect(graph);

        assertTrue(cycles.stream().anyMatch(DeadlockCycle::hasPinningCorrelation),
                "A later pin may correlate globally when carrier identity cannot be matched reliably");
    }

    private static void addDeadlockCycle(WaitForGraph graph) {
        GraphNode threadA = new GraphNode(10, NodeType.VIRTUAL_THREAD, "deadlock-A");
        GraphNode threadB = new GraphNode(11, NodeType.VIRTUAL_THREAD, "deadlock-B");
        GraphNode lockA = new GraphNode(20, NodeType.LOCK, "lock-A");
        GraphNode lockB = new GraphNode(21, NodeType.LOCK, "lock-B");

        graph.addEdge(new GraphEdge(threadA, lockB, EdgeType.WAITS_FOR));
        graph.addEdge(new GraphEdge(lockB, threadB, EdgeType.HOLDS));
        graph.addEdge(new GraphEdge(threadB, lockA, EdgeType.WAITS_FOR));
        graph.addEdge(new GraphEdge(lockA, threadA, EdgeType.HOLDS));
    }
}