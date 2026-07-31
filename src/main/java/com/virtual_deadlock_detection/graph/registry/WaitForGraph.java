package com.virtual_deadlock_detection.graph.registry;

import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WaitForGraph {

    private final Map<GraphNode, Set<GraphEdge>> adjacencyList =
            new ConcurrentHashMap<>();

    public void addNode(GraphNode node) {

        adjacencyList.putIfAbsent(
                node,
                ConcurrentHashMap.newKeySet()
        );
    }

    public void addEdge(GraphEdge edge) {

        addNode(edge.getSource());
        addNode(edge.getTarget());

        adjacencyList.get(edge.getSource())
                .add(edge);
    }

    public void removeEdge(GraphEdge edge) {

        Set<GraphEdge> edges =
                adjacencyList.get(edge.getSource());

        if (edges != null) {

            edges.remove(edge);
        }
    }

    public void removeEdgesTouching(GraphNode node) {

        // Remove all outgoing edges
        adjacencyList.remove(node);

        // Remove all incoming edges
        for (Set<GraphEdge> edges : adjacencyList.values()) {
            edges.removeIf(edge ->
                    edge.getTarget().equals(node));
        }
    }

    public Map<GraphNode, Set<GraphEdge>> getEdges() {

        return Collections.unmodifiableMap(adjacencyList);
    }

    public Set<GraphNode> getNodes() {

        return Collections.unmodifiableSet(
                adjacencyList.keySet()
        );
    }

    public synchronized void rebuild(Collection<GraphEdge> edges) {

        // Ignore invalid snapshots so we don't destroy the current graph
        if (edges == null || edges.isEmpty()) {
            return;
        }

        // Build a new graph first
        Map<GraphNode, Set<GraphEdge>> newAdjacencyList =
                new ConcurrentHashMap<>();

        for (GraphEdge edge : edges) {

            newAdjacencyList
                    .computeIfAbsent(
                            edge.getSource(),
                            k -> ConcurrentHashMap.newKeySet())
                    .add(edge);
        }

        // Replace the live graph only after the new one is ready
        adjacencyList.clear();
        adjacencyList.putAll(newAdjacencyList);
    }


}