package com.virtual_deadlock_detection.graph.detector;

import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Traverses the Wait-For Graph using Depth-First Search (DFS)
 * to detect circular dependencies (deadlocks), then correlates each
 * detected cycle against co-resident PINS edges — the unified
 * deadlock+pinning detection claim.
 */
public class DeadlockDetector {

    // How long a PINS edge must have existed before treating it as
    // correlated with a co-resident cycle, rather than a still-forming or
    // already-resolved transient pin. Matches the intent behind
    // GraphEdge.ageMicros()'s existing javadoc.
    private static final long PINNING_CORRELATION_THRESHOLD_MICROS = 500_000; // 500ms

    /**
     * Scans the graph and returns all detected cycles, each enriched with
     * any correlated pinning evidence found on its member nodes.
     */
    public List<DeadlockCycle> detect(WaitForGraph graph) {
        List<DeadlockCycle> cycles = new ArrayList<>();
        Set<GraphNode> visited = new HashSet<>();
        Set<GraphNode> recursionStack = new HashSet<>();
        Map<GraphNode, GraphEdge> edgeToReach = new HashMap<>();

        for (GraphNode node : graph.getNodes()) {
            if (!visited.contains(node)) {
                findCycleDFS(node, graph, visited, recursionStack, edgeToReach, cycles);
            }
        }

        return cycles.stream()
                .map(cycle -> correlatePinning(cycle, graph))
                .collect(Collectors.toList());
    }

    private void findCycleDFS(GraphNode current, WaitForGraph graph, Set<GraphNode> visited,
                              Set<GraphNode> recursionStack, Map<GraphNode, GraphEdge> edgeToReach,
                              List<DeadlockCycle> cycles) {
        visited.add(current);
        recursionStack.add(current);

        Set<GraphEdge> outgoingEdges = graph.getEdges().getOrDefault(current, Collections.emptySet());
        for (GraphEdge edge : outgoingEdges) {
            GraphNode neighbor = edge.getTarget();

            if (!visited.contains(neighbor)) {
                edgeToReach.put(neighbor, edge);
                findCycleDFS(neighbor, graph, visited, recursionStack, edgeToReach, cycles);
            } else if (recursionStack.contains(neighbor)) {
                // A back-edge was found! This means we have a cycle.
                cycles.add(buildCycle(neighbor, current, edge, edgeToReach));
            }
        }
        // Remove from current path when backtracking
        recursionStack.remove(current);
    }

    /**
     * Reconstructs the exact path of edges that form the cycle.
     */
    private DeadlockCycle buildCycle(GraphNode start, GraphNode end, GraphEdge closingEdge,
                                     Map<GraphNode, GraphEdge> edgeToReach) {
        List<GraphEdge> cycleEdges = new ArrayList<>();
        cycleEdges.add(closingEdge);

        GraphNode curr = end;
        while (!curr.equals(start)) {
            GraphEdge e = edgeToReach.get(curr);
            cycleEdges.add(0, e); // Prepend to keep chronological order
            curr = e.getSource();
        }
        return new DeadlockCycle(cycleEdges, "DFS_CYCLE_DETECTION");
    }

    /**
     * NEW — the unified-detection step, correlated globally by time rather
     * than by graph position.
     *
     * Ideally this would only look at PINS edges sourced from nodes IN this
     * cycle. However, ThreadMXBean (which ThreadStatePoller relies on to
     * detect permanent blocks — see its javadoc) attributes a monitor held
     * by a virtual thread to its CARRIER platform thread (e.g.
     * "ForkJoinPool-1-worker-1"), not to the virtual thread itself. This is
     * a documented JDK 21 limitation with no public API workaround — JFR's
     * own jdk.VirtualThreadPinned event has no carrier-identity field
     * either (see JfrEventListener's TODO). A HOLDS edge and its
     * corresponding PINS edge can therefore land on two different node
     * identities for what is, physically, the same thread of execution.
     *
     * Until that's resolved with a lower-level (JVMTI) carrier-tracking
     * bridge, correlate any PINS edge old enough to be a real, settled pin
     * — anywhere in the graph — with any cycle detected while it's active.
     */
    private DeadlockCycle correlatePinning(DeadlockCycle cycle, WaitForGraph graph) {

        List<GraphEdge> correlatedPins = new ArrayList<>();
        for (GraphNode node : graph.getNodes()) {
            Set<GraphEdge> outgoing = graph.getEdges().getOrDefault(node, Collections.emptySet());
            for (GraphEdge edge : outgoing) {
                if (edge.getType() == EdgeType.PINS
                        && edge.ageMicros() >= PINNING_CORRELATION_THRESHOLD_MICROS) {
                    correlatedPins.add(edge);
                }
            }
        }

        if (correlatedPins.isEmpty()) {
            return cycle;
        }

        return new DeadlockCycle(cycle.getEdgesInCycle(), cycle.getDetectionStrategy(), correlatedPins);
    }
}