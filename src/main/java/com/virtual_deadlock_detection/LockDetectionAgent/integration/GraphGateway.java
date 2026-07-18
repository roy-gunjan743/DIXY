package com.virtual_deadlock_detection.LockDetectionAgent.integration;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
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

            case THREAD_PARKED -> {

                System.out.println("Thread Parked : "
                        + threadNode.getLabel());
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

                // NEW — reverse edge so a real deadlock can actually close
                // into a cycle. Requires JfrEventListener's previousOwner fix above.
                if (eventDTO.getOwnerThreadId() != 0) {

                    GraphNode ownerNode = new GraphNode(
                            eventDTO.getOwnerThreadId(),
                            NodeType.VIRTUAL_THREAD,
                            eventDTO.getOwnerThreadName()
                    );

                    waitForGraph.addNode(ownerNode);

                    GraphEdge holdsEdge = new GraphEdge(
                            lockNode,
                            ownerNode,
                            EdgeType.HOLDS
                    );

                    waitForGraph.addEdge(holdsEdge);

                    System.out.println(holdsEdge);
                }
            }

            case LOCK_OWNED -> {

                // From the native JVMTI path (NativeEventBridge.publishLockOwned).
                // Was previously falling through to `default` and being dropped silently.
                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );

                GraphNode ownerNode = new GraphNode(
                        eventDTO.getOwnerThreadId(),
                        NodeType.VIRTUAL_THREAD,
                        eventDTO.getOwnerThreadName()
                );

                waitForGraph.addNode(lockNode);
                waitForGraph.addNode(ownerNode);

                GraphEdge edge = new GraphEdge(lockNode, ownerNode, EdgeType.HOLDS);
                waitForGraph.addEdge(edge);

                System.out.println(edge);
            }

            case LOCK_RELEASE -> {

                System.out.println(
                        "Lock Released : "
                                + eventDTO.getLockAddress());

                // Remove waiting edge here when your WaitForGraph supports it.
            }

            case PINNING -> {

                GraphNode carrierNode = new GraphNode(
                        eventDTO.getCarrierThreadId(),
                        NodeType.CARRIER_THREAD,
                        eventDTO.getCarrierThreadName()
                );

                waitForGraph.addNode(threadNode);
                waitForGraph.addNode(carrierNode);

                GraphEdge edge = new GraphEdge(
                        threadNode,
                        carrierNode,
                        EdgeType.PINS
                );

                waitForGraph.addEdge(edge);

                System.out.println("Pinning Edge Added: " + edge);
            }

            default -> {

                System.out.println("Unsupported Event : "
                        + eventDTO.getEventType());
            }
        }

        detectDeadlocks();
    }

    private void detectDeadlocks() {

        List<DeadlockCycle> cycles = detector.detect(waitForGraph);

        if (cycles.isEmpty()) {

            System.out.println("No Deadlock");

        } else {

            System.out.println();
            System.out.println("======================================");
            System.out.println("      DEADLOCK DETECTED");
            System.out.println("======================================");

            for (DeadlockCycle cycle : cycles) {
                System.out.println(cycle);
            }
        }
    }
}