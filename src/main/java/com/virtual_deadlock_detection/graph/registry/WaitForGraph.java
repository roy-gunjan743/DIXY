package com.virtual_deadlock_detection.graph.registry;

import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe directed graph representing the wait/hold/pin relationships.
 */
public class WaitForGraph {

    // 1. The main map is thread-safe
    private final Map<GraphNode, Set<GraphEdge>> adjacencyList = new ConcurrentHashMap<>();

    public void addNode(GraphNode node) {
        // 2. putIfAbsent is an ATOMIC operation (thread-safe).
        // 3. newKeySet() creates a thread-safe Set for the edges.
        adjacencyList.putIfAbsent(node, ConcurrentHashMap.newKeySet());
    }

    public void addEdge(GraphEdge edge) {
        addNode(edge.getSource());
        addNode(edge.getTarget());

        // Multiple virtual threads can safely add to this set simultaneously
        adjacencyList.get(edge.getSource()).add(edge);
    }

    public void removeEdge(GraphEdge edge) {
        Set<GraphEdge> edges = adjacencyList.get(edge.getSource());
        if (edges != null) {
            edges.remove(edge);
        }
    }

    public Map<GraphNode, Set<GraphEdge>> getEdges() {
        // Returns a read-only view to prevent accidental modifications outside this class
        return Collections.unmodifiableMap(adjacencyList);
    }

    public Set<GraphNode> getNodes() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }
}