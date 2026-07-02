package com.virtual_deadlock_detection.graph.algorithm;

import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.util.*;

/**
 * Advanced graph algorithms for Wait-For Graph analysis.
 */
public class GraphAlgorithms {

    /**
     * Executes Tarjan's Strongly Connected Components (SCC) algorithm.
     * This groups complex webs of intertwined deadlocks into single clusters.
     */
    public static List<DeadlockCycle> detectTarjanSCC(WaitForGraph graph) {
        List<DeadlockCycle> deadlockClusters = new ArrayList<>();
        Map<GraphNode, Integer> indices = new HashMap<>();
        Map<GraphNode, Integer> lowlinks = new HashMap<>();
        Set<GraphNode> onStack = new HashSet<>();
        Deque<GraphNode> stack = new ArrayDeque<>();

        // Array used as a mutable integer reference
        int[] index = {0};

        for (GraphNode v : graph.getNodes()) {
            if (!indices.containsKey(v)) {
                strongConnect(v, graph, indices, lowlinks, onStack, stack, index, deadlockClusters);
            }
        }
        return deadlockClusters;
    }

    private static void strongConnect(GraphNode v, WaitForGraph graph,
                                      Map<GraphNode, Integer> indices, Map<GraphNode, Integer> lowlinks,
                                      Set<GraphNode> onStack, Deque<GraphNode> stack, int[] index,
                                      List<DeadlockCycle> deadlockClusters) {
        indices.put(v, index[0]);
        lowlinks.put(v, index[0]);
        index[0]++;
        stack.push(v);
        onStack.add(v);

        Set<GraphEdge> edges = graph.getEdges().getOrDefault(v, Collections.emptySet());
        for (GraphEdge edge : edges) {
            GraphNode w = edge.getTarget();
            if (!indices.containsKey(w)) {
                // Successor w has not yet been visited; recurse on it
                strongConnect(w, graph, indices, lowlinks, onStack, stack, index, deadlockClusters);
                lowlinks.put(v, Math.min(lowlinks.get(v), lowlinks.get(w)));
            } else if (onStack.contains(w)) {
                // Successor w is in stack S and hence in the current SCC
                lowlinks.put(v, Math.min(lowlinks.get(v), indices.get(w)));
            }
        }

        // If v is a root node, pop the stack and generate an SCC
        if (lowlinks.get(v).equals(indices.get(v))) {
            Set<GraphNode> sccNodes = new HashSet<>();
            GraphNode w;
            do {
                w = stack.pop();
                onStack.remove(w);
                sccNodes.add(w);
            } while (!w.equals(v));

            // An SCC with more than 1 node means a circular dependency (deadlock).
            // (Size 1 is ignored unless a thread somehow waits on itself).
            if (sccNodes.size() > 1 || hasSelfLoop(v, edges)) {
                deadlockClusters.add(buildClusterCycle(sccNodes, graph));
            }
        }
    }

    private static boolean hasSelfLoop(GraphNode v, Set<GraphEdge> edges) {
        return edges.stream().anyMatch(e -> e.getTarget().equals(v));
    }

    /**
     * Converts a Tarjan node cluster back into the edge-list format
     * that DeadlockReportGenerator expects.
     */
    private static DeadlockCycle buildClusterCycle(Set<GraphNode> sccNodes, WaitForGraph graph) {
        List<GraphEdge> clusterEdges = new ArrayList<>();

        // Extract all edges that exist exclusively inside this deadlock cluster
        for (GraphNode node : sccNodes) {
            Set<GraphEdge> edges = graph.getEdges().getOrDefault(node, Collections.emptySet());
            for (GraphEdge edge : edges) {
                if (sccNodes.contains(edge.getTarget())) {
                    clusterEdges.add(edge);
                }
            }
        }
        return new DeadlockCycle(clusterEdges, "TARJAN_SCC_CLUSTER");
    }
}