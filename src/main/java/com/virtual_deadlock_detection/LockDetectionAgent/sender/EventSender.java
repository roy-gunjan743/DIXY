package com.virtual_deadlock_detection.LockDetectionAgent.sender;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.integration.GraphGateway;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

public class EventSender {

    // Was: private static final WaitForGraph GRAPH = new WaitForGraph();
    // That created a graph that lived only inside EventSender, invisible
    // to anything reading RuntimeContext.GRAPH. Now points at the single
    // shared graph.
    private final GraphGateway graphGateway =
            new GraphGateway(RuntimeContext.GRAPH);

    public void send(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Sending Event To Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name : " + eventDTO.getThreadName());
        System.out.println("--------------------------------");

        graphGateway.processEvent(eventDTO);
    }

    public static WaitForGraph getGraph() {
        return RuntimeContext.GRAPH;
    }
}