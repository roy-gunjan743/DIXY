package com.virtual_deadlock_detection.graph.registry;

import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;

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

    public Map<GraphNode, Set<GraphEdge>> getEdges() {

        return Collections.unmodifiableMap(adjacencyList);
    }

    public Set<GraphNode> getNodes() {

        return Collections.unmodifiableSet(
                adjacencyList.keySet()
        );
    }
}