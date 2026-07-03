package com.virtual_deadlock_detection.integration;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.events.EventType;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import java.util.List;

public class GraphGateway {

    private final WaitForGraph waitForGraph;
    private final DeadlockDetector detector = new DeadlockDetector();

    public GraphGateway(WaitForGraph waitForGraph) {
        this.waitForGraph = waitForGraph;
    }

    public void processEvent(EventDTO eventDTO) {

        System.out.println("================================");
        System.out.println("Graph Updated");
        System.out.println("================================");

        GraphNode threadNode = new GraphNode(
                eventDTO.getThreadId(),
                NodeType.VIRTUAL_THREAD,
                eventDTO.getThreadName()
        );

        switch (eventDTO.getEventType()) {

            case THREAD_START -> {

                waitForGraph.addNode(threadNode);

                System.out.println("Virtual Thread Added");
            }

            case THREAD_END -> {

                System.out.println("Thread End Event Received");
                // Node removal can be implemented later.
            }

            case LOCK_ACQUIRE,
                 THREAD_BLOCKED -> {

                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );

                waitForGraph.addNode(threadNode);
                waitForGraph.addNode(lockNode);

                GraphEdge edge = new GraphEdge(
                        threadNode,
                        lockNode,
                        EdgeType.WAITS_FOR
                );

                waitForGraph.addEdge(edge);

                System.out.println(edge);
            }

            default -> {

                System.out.println("Unsupported Event : "
                        + eventDTO.getEventType());
            }
        }

        detectDeadlocks();
    }

    private void detectDeadlocks() {

        List<DeadlockCycle> cycles =
                detector.detect(waitForGraph);

        if (cycles.isEmpty()) {

            System.out.println("No Deadlock");

        } else {

            System.out.println();
            System.out.println("======================================");
            System.out.println(" DEADLOCK DETECTED ");
            System.out.println("======================================");

            for (DeadlockCycle cycle : cycles) {

                System.out.println(cycle);
            }
        }
    }
}