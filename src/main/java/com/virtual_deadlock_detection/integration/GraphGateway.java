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

    private final WaitForGraph graph;
    private final DeadlockDetector detector = new DeadlockDetector();

    public GraphGateway(WaitForGraph graph) {
        this.graph = graph;
    }

    public void processEvent(EventDTO event) {

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

        graph.addNode(threadNode);
        graph.addNode(lockNode);

        switch (event.getEventType()) {

            case THREAD_START -> {

                System.out.println("Thread Started : "
                        + threadNode.getLabel());
            }

            case THREAD_END -> {

                System.out.println("Thread Ended : "
                        + threadNode.getLabel());
            }

            case LOCK_ACQUIRE,
                 THREAD_BLOCKED -> {

                GraphEdge waitEdge =
                        new GraphEdge(
                                threadNode,
                                lockNode,
                                EdgeType.WAITS_FOR
                        );

                graph.addEdge(waitEdge);

                System.out.println(waitEdge);
            }

            case LOCK_OWNED -> {

                GraphNode ownerNode =
                        new GraphNode(
                                event.getOwnerThreadId(),
                                NodeType.VIRTUAL_THREAD,
                                event.getOwnerThreadName()
                        );

                graph.addNode(ownerNode);

                GraphEdge holdEdge =
                        new GraphEdge(
                                ownerNode,
                                lockNode,
                                EdgeType.HOLDS
                        );

                graph.addEdge(holdEdge);

                System.out.println(holdEdge);
            }

            case LOCK_RELEASE -> {

                System.out.println(
                        "Lock Released : "
                                + lockNode.getLabel()
                );
            }

            default -> {

                System.out.println(
                        "Unhandled Event : "
                                + event.getEventType()
                );
            }
        }

        detectDeadlocks();
    }

    private void detectDeadlocks() {

        List<DeadlockCycle> cycles =
                detector.detect(graph);

        if (cycles.isEmpty()) {

            System.out.println();
            System.out.println("--------------------------------");
            System.out.println("No Deadlock");
            System.out.println("--------------------------------");

        } else {

            System.out.println();
            System.out.println("================================");
            System.out.println(" DEADLOCK DETECTED ");
            System.out.println("================================");

            for (DeadlockCycle cycle : cycles) {

                System.out.println(cycle);
            }
        }
    }
}