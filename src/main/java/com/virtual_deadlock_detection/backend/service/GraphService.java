package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Builds the wait-for graph and runs cycle detection on it.
 *
 * IMPORTANT: {@code detectDeadlocks()} used to run the detector against the
 * live, in-memory {@code RuntimeContext.GRAPH} singleton. That graph is only
 * ever populated by an in-process JVMTI/JFR agent attached to *this* JVM
 * (via GraphEngine.refresh() reading ThreadRegistry/LockRegistry/
 * WaitingRegistry). Nothing else feeds it: neither the CLI's
 * "deadlocks seed" command nor the /api/telemetry HTTP ingestion path touch
 * it — both only write to Postgres. As a result "deadlocks detect" would
 * always report zero cycles in a normal CLI/API session, even immediately
 * after seeding demo data, while "status" (which separately rebuilds a
 * graph from the DB) showed the deadlock correctly.
 *
 * This now rebuilds the graph from the same source of truth as "status" —
 * the wait_edges table — so every command agrees on what's actually stored.
 */
@Service
public class GraphService {

    private final WaitEdgeRepository waitEdgeRepository;
    private final DeadlockDetector detector;

    public GraphService(WaitEdgeRepository waitEdgeRepository) {
        this.waitEdgeRepository = waitEdgeRepository;
        this.detector = RuntimeContext.DETECTOR;
    }

    /**
     * Runs deadlock detection against the current state of the database
     * (the wait_edges table), not the agent-only live registry.
     */
    @Transactional(readOnly = true)
    public List<DeadlockCycle> detectDeadlocks() {
        return detector.detect(buildGraphFromDb());
    }

    /**
     * Rebuilds a fresh WaitForGraph from all currently stored wait-edges.
     * Shared by DeadlockCommands' "status" and "deadlocks detect" so both
     * commands always see the same graph.
     *
     * Uses findAllWithThreadsAndLocks() (a JOIN FETCH query) rather than
     * plain findAll(), so waitingThread / lock / lock.ownerThread are
     * loaded eagerly in one round trip instead of as LAZY proxies. That
     * avoids LazyInitializationException when this runs outside of an HTTP
     * request (e.g. from the Spring Shell CLI), where there's no
     * Open-Session-In-View to keep a Hibernate session alive after the
     * query returns. @Transactional here is a second safety net for any
     * other caller.
     */
    @Transactional(readOnly = true)
    public WaitForGraph buildGraphFromDb() {
        WaitForGraph graph = new WaitForGraph();

        for (WaitEdgeEntity edgeEntity : waitEdgeRepository.findAllWithThreadsAndLocks()) {
            ThreadEntity waiting = edgeEntity.getWaitingThread();
            LockEntity lock = edgeEntity.getLock();
            if (waiting == null || lock == null) continue;

            ThreadEntity owner = lock.getOwnerThread();
            if (owner == null) continue;
            if (waiting.getThreadId().equals(owner.getThreadId())) continue;

            GraphNode src = new GraphNode(waiting.getThreadId(), NodeType.VIRTUAL_THREAD, waiting.getThreadName());
            GraphNode tgt = new GraphNode(owner.getThreadId(), NodeType.VIRTUAL_THREAD, owner.getThreadName());
            graph.addEdge(new GraphEdge(src, tgt, EdgeType.WAITS_FOR));
        }

        return graph;
    }
}