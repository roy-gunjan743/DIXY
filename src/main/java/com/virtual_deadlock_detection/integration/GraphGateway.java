package com.virtual_deadlock_detection.integration;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

public class GraphGateway {
    private final WaitForGraph waitForGraph = new WaitForGraph();

    public void sendToGraph(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Forwarding Event To Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name: " + eventDTO.getThreadName());
        System.out.println("--------------------------------");
        GraphNode threadNode = new GraphNode(
                eventDTO.getThreadId(),
                NodeType.VIRTUAL_THREAD,
                eventDTO.getThreadName()
        );
        switch (eventDTO.getEventType()) {

            case THREAD_START -> {
                waitForGraph.addNode(threadNode);
                System.out.println("Virtual Thread Added To Graph");
            }

            case THREAD_END -> {
                System.out.println("Thread End Event Received");
                // Node removal will be implemented later
            }

            default -> {
                System.out.println("Event not supported yet");
            }

        }
        System.out.println("Graph Nodes: " + waitForGraph.getNodes());
        /*
            Member 2 integration happens here.

            Example:

            waitForGraph.processEvent(eventDTO);

        */

    }

}