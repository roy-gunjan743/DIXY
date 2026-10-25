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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GraphGateway {

    private static final Logger log = LoggerFactory.getLogger(GraphGateway.class);
    private final WaitForGraph waitForGraph;
    private final DeadlockDetector detector = RuntimeContext.DETECTOR;

    private final Map<String, Instant> reportedCycles = new ConcurrentHashMap<>();
    private static final Duration REPORT_COOLDOWN = Duration.ofSeconds(30);

    public GraphGateway(WaitForGraph waitForGraph) {
        this.waitForGraph = waitForGraph;
    }

    // Events arrive concurrently from the JVMTI callback, the JFR stream and
    // the poller. Mutating the graph while another thread runs the DFS made
    // the detector see half-applied updates. A ReentrantLock (parks, takes no
    // monitor) serialises update+detect without generating the very monitor
    // events this agent is watching for.
    private final ReentrantLock graphLock = new ReentrantLock();

    public void processEvent(EventDTO eventDTO) {
        List<DeadlockCycle> toReport;

        graphLock.lock();
        try {
            applyEvent(eventDTO);
            toReport = collectNewCycles();
        } finally {
            graphLock.unlock();
        }

        // Reporting is an async HTTP call - keep it outside the lock.
        for (DeadlockCycle cycle : toReport) {
            log.debug("Deadlock detected cycle: {}", cycle);
            RuntimeContext.REPORT_SINK.report(cycle);
        }
    }

    private void applyEvent(EventDTO eventDTO) {
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

            case LOCK_ACQUIRE -> {
                // Source: JFR jdk.JavaMonitorEnter. JFR emits that event when
                // the thread has ALREADY got the monitor (it carries the wait
                // duration), so it describes a finished wait, not a live
                // one. Recording it as WAITS_FOR left an edge that nothing
                // ever removed (its lock id is a native address, which no
                // other source uses) and it later closed false cycles with
                // live edges from the poller. Treat it as "wait is over".
                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );
                waitForGraph.removeEdge(new GraphEdge(
                        threadNode,
                        lockNode,
                        EdgeType.WAITS_FOR
                ));
            }

            case THREAD_BLOCKED -> {
                // A blocked event with no known owner cannot be part of a
                // cycle (nothing to follow from the lock). This is what
                // JFR's jdk.JavaMonitorWait (Object.wait) produces - it is
                // not contention for a monitor owned by someone else.
                if (eventDTO.getOwnerThreadId() == 0) {
                    log.debug("Ignoring THREAD_BLOCKED without owner: {}", threadNode.getLabel());
                    return;
                }

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

                    // A monitor has exactly one owner: drop the previous one.
                    waitForGraph.removeOutgoing(lockNode, EdgeType.HOLDS);
                    waitForGraph.addEdge(holdsEdge);
                    log.debug("Added holds edge: {}", holdsEdge);
                }
            }

            case LOCK_OWNED -> {

                /*
                 * The native JVMTI event tells us:
                 *
                 * waitingThread -> lock -> ownerThread
                 *
                 * Therefore we must create BOTH graph relationships:
                 *
                 * waitingThread --WAITS_FOR--> lock
                 * lock          --HOLDS-----> ownerThread
                 */

                GraphNode waitingNode = new GraphNode(
                        eventDTO.getThreadId(),
                        NodeType.VIRTUAL_THREAD,
                        eventDTO.getThreadName()
                );

                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );

                waitForGraph.addNode(waitingNode);
                waitForGraph.addNode(lockNode);

                /*
                 * Waiting thread is waiting for this lock.
                 */
                GraphEdge waitsEdge = new GraphEdge(
                        waitingNode,
                        lockNode,
                        EdgeType.WAITS_FOR
                );

                waitForGraph.addEdge(waitsEdge);

                log.debug(
                        "Added native JVMTI wait edge: {}",
                        waitsEdge
                );

                /*
                 * The owner currently holds the lock.
                 */
                if (eventDTO.getOwnerThreadId() != 0
                        && eventDTO.getOwnerThreadId() != eventDTO.getThreadId()) {

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

                    // A monitor has exactly one owner: drop the previous one.
                    waitForGraph.removeOutgoing(lockNode, EdgeType.HOLDS);
                    waitForGraph.addEdge(holdsEdge);

                    log.debug(
                            "Added native JVMTI holds edge: {}",
                            holdsEdge
                    );
                }
            }

            case LOCK_RELEASE -> {
                GraphNode lockNode = new GraphNode(
                        eventDTO.getLockAddress(),
                        NodeType.LOCK,
                        "Monitor-" + eventDTO.getLockAddress()
                );

                // This event means the thread is no longer waiting on the
                // lock (JVMTI MonitorContendedEntered, or the poller seeing
                // the thread leave BLOCKED). The WAITS_FOR edge created when
                // the wait began must go, otherwise it stays forever and
                // later joins up with other stale edges into a false cycle.
                GraphEdge waitsEdge = new GraphEdge(
                        threadNode,
                        lockNode,
                        EdgeType.WAITS_FOR
                );
                waitForGraph.removeEdge(waitsEdge);
                log.debug("Removed wait edge: {}", waitsEdge);

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
    }

    private List<DeadlockCycle> collectNewCycles() {
        Instant now = Instant.now();

        // Forget old signatures so the map cannot grow without bound.
        reportedCycles.values().removeIf(
                last -> Duration.between(last, now).compareTo(REPORT_COOLDOWN) > 0);

        List<DeadlockCycle> fresh = new ArrayList<>();
        for (DeadlockCycle cycle : detector.detect(waitForGraph)) {
            // Identify a deadlock by the set of THREADS involved, not by its
            // edges. Edge-based signatures included lock ids and traversal
            // order, so the same persistent deadlock produced a new
            // signature on almost every event and was re-reported hundreds
            // of times instead of once per cooldown window.
            // Names, not ids: the JVMTI bridge and the ThreadMXBean poller
            // number the same thread differently, so one real deadlock can
            // show up as two id sets but always has one set of names.
            String signature = cycle.getCycle().stream()
                    .filter(GraphNode::isThread)
                    .map(GraphNode::getLabel)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));

            if (!reportedCycles.containsKey(signature)) {
                reportedCycles.put(signature, now);
                fresh.add(cycle);
            }
        }
        return fresh;
    }
}

