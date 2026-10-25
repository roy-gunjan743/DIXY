package com.virtual_deadlock_detection.graph.registry;

import com.virtual_deadlock_detection.graph.model.EdgeType;
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

    /**
     * Removes every outgoing edge of the given type from a node. Used to
     * enforce "a monitor has exactly one owner": before recording a new
     * HOLDS edge for a lock the previous owner's edge has to go, otherwise
     * stale ownership shows up later as a phantom cycle.
     */
    public void removeOutgoing(GraphNode node, EdgeType type) {

        Set<GraphEdge> edges = adjacencyList.get(node);

        if (edges != null) {

            edges.removeIf(edge -> edge.getType() == type);
        }
    }

    public synchronized void rebuild(Collection<GraphEdge> edges) {

        // A null snapshot is invalid and must not destroy the current graph.
        // An EMPTY snapshot is valid: it means nobody is waiting on anything
        // any more, so the old edges (and the cycles they imply) must go.
        // Returning early here used to leave resolved deadlocks in the
        // graph forever.
        if (edges == null) {
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