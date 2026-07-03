package com.virtual_deadlock_detection.sender;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.integration.GraphGateway;

public class EventSender {

    private static final WaitForGraph GRAPH = new WaitForGraph();

    private final GraphGateway graphGateway =
            new GraphGateway(GRAPH);

    public void send(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Sending Event To Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name : " + eventDTO.getThreadName());
        System.out.println("--------------------------------");

        graphGateway.processEvent(eventDTO);
    }

    public static WaitForGraph getGraph() {
        return GRAPH;
    }
}