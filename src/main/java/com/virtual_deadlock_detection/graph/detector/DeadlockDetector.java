package com.virtual_deadlock_detection.graph.detector;

import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.util.*;

/**
 * Traverses the Wait-For Graph using Depth-First Search (DFS)
 * to detect circular dependencies (deadlocks).
 */
public class DeadlockDetector {

    /**
     * Scans the graph and returns all detected cycles.
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
        return cycles;
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
}