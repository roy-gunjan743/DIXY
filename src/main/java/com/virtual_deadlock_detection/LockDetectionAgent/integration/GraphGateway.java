package com.virtual_deadlock_detection.LockDetectionAgent.integration;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.LockDetectionAgent.logging.Logger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import java.util.List;

public class GraphGateway {

    private static final Logger log = Logger.getLogger(GraphGateway.class);
    private final WaitForGraph waitForGraph;
    private final DeadlockDetector detector = RuntimeContext.DETECTOR;

    private final Map<String, Instant> reportedCycles = new ConcurrentHashMap<>();
    private static final Duration REPORT_COOLDOWN = Duration.ofSeconds(30);

    public GraphGateway(WaitForGraph waitForGraph) {
        this.waitForGraph = waitForGraph;
    }

    public void processEvent(EventDTO eventDTO) {
        GraphNode threadNode = new GraphNode(
                eventDTO.getThreadId(),
                NodeType.VIRTUAL_THREAD,
                eventDTO.getThreadName()
        );

        switch (eventDTO.getEventType()) {
            case THREAD_START -> {
                waitForGraph.addNode(threadNode);
                log.debug("Virtual Thread Added: {}", threadNode.getLabel());
            }

            case THREAD_END -> {
                waitForGraph.removeEdgesTouching(threadNode);
                log.debug("Removed graph edges for {}", threadNode.getLabel());
            }

            case THREAD_PARKED -> {
                log.debug("Thread Parked: {}", threadNode.getLabel());
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
                log.debug("Added edge: {}", edge);

                if (eventDTO.getOwnerThreadId() != 0 && eventDTO.getOwnerThreadId() != eventDTO.getThreadId()) {
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
                    log.debug("Added holds edge: {}", holdsEdge);
                }
            }

            case LOCK_OWNED -> {
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
                log.debug("Added lock owned edge: {}", edge);
            }

            case LOCK_RELEASE -> {
                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );

                GraphEdge holdsEdge = new GraphEdge(
                        lockNode,
                        threadNode,
                        EdgeType.HOLDS
                );

                waitForGraph.removeEdge(holdsEdge);
                log.debug("Removed holds edge: {}", holdsEdge);
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
                log.debug("Pinning edge added: {}", edge);
            }

            default -> {
                log.debug("Unsupported Event: {}", eventDTO.getEventType());
            }
        }

        detectDeadlocks();
    }

    private void detectDeadlocks() {
        List<DeadlockCycle> cycles = detector.detect(waitForGraph);
        for (DeadlockCycle cycle : cycles) {
            String signature = cycle.getEdgesInCycle().stream()
                    .map(e -> e.getSource().getId() + "->" + e.getTarget().getId())
                    .sorted()
                    .collect(Collectors.joining("|"));

            Instant last = reportedCycles.get(signature);
            Instant now = Instant.now();
            if (last == null || Duration.between(last, now).compareTo(REPORT_COOLDOWN) > 0) {
                reportedCycles.put(signature, now);
                log.debug("Deadlock detected cycle: {}", cycle);
                RuntimeContext.REPORT_SINK.report(cycle);
            }
        }
    }
}

