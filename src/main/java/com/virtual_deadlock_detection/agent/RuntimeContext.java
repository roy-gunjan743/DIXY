package com.virtual_deadlock_detection.agent;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.engine.GraphEngine;
import com.virtual_deadlock_detection.graph.registry.*;

public class RuntimeContext {

    private static final ThreadRegistry THREAD_REGISTRY =
            new ThreadRegistry();

    private static final LockRegistry LOCK_REGISTRY =
            new LockRegistry();

    private static final WaitingRegistry WAITING_REGISTRY =
            new WaitingRegistry();

    private static final WaitForGraph GRAPH =
            new WaitForGraph();

    private static final DeadlockDetector DETECTOR =
            new DeadlockDetector();

    private static final GraphEngine GRAPH_ENGINE =
            new GraphEngine(
                    THREAD_REGISTRY,
                    LOCK_REGISTRY,
                    WAITING_REGISTRY,
                    GRAPH,
                    DETECTOR
            );

    public static ThreadRegistry threads() {
        return THREAD_REGISTRY;
    }

    public static LockRegistry locks() {
        return LOCK_REGISTRY;
    }

    public static WaitingRegistry waiting() {
        return WAITING_REGISTRY;
    }

    public static WaitForGraph graph() {
        return GRAPH;
    }

    public static GraphEngine engine() {
        return GRAPH_ENGINE;
    }

}