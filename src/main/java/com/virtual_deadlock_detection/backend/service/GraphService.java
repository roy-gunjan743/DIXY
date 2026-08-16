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
 * Rebuilds the graph from the wait_edges table (the single source of truth)
 * populated by real ingested telemetry so every query path (CLI status,
 * deadlocks detect, web dashboard) agrees on what is currently active.
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
            if (waiting.getId().equals(owner.getId())) continue;

            String srcLabel = waiting.getThreadName() != null ? waiting.getThreadName() : "thread-" + waiting.getThreadId();
            String tgtLabel = owner.getThreadName() != null ? owner.getThreadName() : "thread-" + owner.getThreadId();

            GraphNode src = new GraphNode(waiting.getId(), NodeType.VIRTUAL_THREAD, srcLabel);
            GraphNode tgt = new GraphNode(owner.getId(), NodeType.VIRTUAL_THREAD, tgtLabel);
            graph.addEdge(new GraphEdge(src, tgt, EdgeType.WAITS_FOR));
        }

        return graph;
    }
}