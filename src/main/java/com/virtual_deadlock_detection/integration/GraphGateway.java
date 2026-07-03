package com.virtual_deadlock_detection.integration;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.events.EventType;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.*;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import java.util.List;

public class GraphGateway {

    private final WaitForGraph graph;

    private final DeadlockDetector detector;

    public GraphGateway(WaitForGraph graph) {

        this.graph = graph;

        this.detector = new DeadlockDetector();
    }

    public void processEvent(EventDTO event) {

        if (event.getEventType() != EventType.LOCK_ACQUIRE
                && event.getEventType() != EventType.THREAD_BLOCKED) {

            return;
        }

        GraphNode threadNode = new GraphNode(
                event.getThreadId(),
                NodeType.VIRTUAL_THREAD,
                event.getThreadName()
        );

        GraphNode lockNode = new GraphNode(
                event.getLockAddress(),
                NodeType.LOCK,
                "Monitor-" + event.getLockAddress()
        );

        GraphEdge edge = new GraphEdge(
                threadNode,
                lockNode,
                EdgeType.WAITS_FOR
        );

        graph.addEdge(edge);

        System.out.println();
        System.out.println("================================");
        System.out.println("Graph Updated");
        System.out.println(edge);
        System.out.println("================================");

        detectDeadlocks();
    }

    private void detectDeadlocks() {

        List<DeadlockCycle> cycles = detector.detect(graph);

        if (cycles.isEmpty()) {

            System.out.println("No Deadlock");

            return;
        }

        System.out.println();
        System.out.println("================================");
        System.out.println("DEADLOCK DETECTED");
        System.out.println("Cycles : " + cycles.size());

        for (DeadlockCycle cycle : cycles) {

            System.out.println(cycle);

        }

        System.out.println("================================");
    }
}