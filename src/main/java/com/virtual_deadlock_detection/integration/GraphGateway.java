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

        // Handle only lock acquire and thread blocked events
        if (event.getEventType() == EventType.LOCK_ACQUIRE
                || event.getEventType() == EventType.THREAD_BLOCKED) {

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

            GraphEdge edge = new GraphEdge(
                    threadNode,
                    lockNode,
                    EdgeType.WAITS_FOR
            );

            graph.addEdge(edge);

            System.out.println("--------------------------------");
            System.out.println("Forwarding Event To Graph");
            System.out.println("Thread : " + event.getThreadName());
            System.out.println("Lock   : " + lockAddress);
            System.out.println("Edge   : WAITS_FOR");
            System.out.println("--------------------------------");
        }
    }
}