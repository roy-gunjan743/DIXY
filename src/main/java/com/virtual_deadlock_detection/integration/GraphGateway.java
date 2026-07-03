package com.virtual_deadlock_detection.integration;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.events.EventType;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

public class GraphGateway {

    private final WaitForGraph graph;

    public GraphGateway(WaitForGraph graph) {
        this.graph = graph;
    }

    public void processEvent(EventDTO event) {
        // MATCHING YOUR ENUM: We care about LOCK_ACQUIRE and THREAD_BLOCKED
        if (event.getEventType() == EventType.LOCK_ACQUIRE || event.getEventType() == EventType.THREAD_BLOCKED) {

            GraphNode threadNode = new GraphNode(
                    event.getThreadId(),
                    NodeType.VIRTUAL_THREAD,
                    event.getThreadName()
            );

            long lockAddress = event.getLockAddress();
            GraphNode lockNode = new GraphNode(
                    lockAddress,
                    NodeType.LOCK,
                    "Monitor-" + lockAddress
            );

            GraphEdge edge = new GraphEdge(threadNode, lockNode, EdgeType.WAITS_FOR);
            graph.addEdge(edge);

            System.out.println("🌉 Gateway mapped edge: " + threadNode.getLabel() + " WAITS_FOR " + lockNode.getLabel());
        }
    }



}