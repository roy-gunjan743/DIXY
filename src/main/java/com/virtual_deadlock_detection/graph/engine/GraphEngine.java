package com.virtual_deadlock_detection.graph.engine;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.registry.LockRegistry;
import com.virtual_deadlock_detection.graph.registry.ThreadRegistry;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.registry.WaitingRegistry;
import com.virtual_deadlock_detection.graph.resolver.OwnerEdge;
import com.virtual_deadlock_detection.graph.resolver.OwnerResolver;

import java.util.List;
import java.util.Set;

public class GraphEngine {

    private final ThreadRegistry threadRegistry;
    private final LockRegistry lockRegistry;
    private final WaitingRegistry waitingRegistry;

    private final OwnerResolver ownerResolver;

    private final GraphBuilder graphBuilder;

    private final WaitForGraph graph;

    private final DeadlockDetector detector;

    public GraphEngine(ThreadRegistry threadRegistry,
                       LockRegistry lockRegistry,
                       WaitingRegistry waitingRegistry,
                       WaitForGraph graph,
                       DeadlockDetector detector) {

        this.threadRegistry = threadRegistry;
        this.lockRegistry = lockRegistry;
        this.waitingRegistry = waitingRegistry;
        this.graph = graph;
        this.detector = detector;

        this.ownerResolver =
                new OwnerResolver(
                        lockRegistry,
                        waitingRegistry
                );

        this.graphBuilder =
                new GraphBuilder(threadRegistry);
    }

    /**
     * Rebuilds graph from runtime state.
     */
    public void refresh() {

        List<OwnerEdge> ownerEdges =
                ownerResolver.resolve();

        Set<GraphEdge> graphEdges =
                graphBuilder.build(ownerEdges);

        graph.rebuild(graphEdges);

    }

    /**
     * Detect deadlocks.
     */
    public List<DeadlockCycle> detectDeadlocks() {

        refresh();

        return detector.detect(graph);

    }

}