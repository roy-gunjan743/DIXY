package com.virtual_deadlock_detection.runtime;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.engine.GraphEngine;
import com.virtual_deadlock_detection.graph.registry.*;

public final class RuntimeContext {

    private RuntimeContext() {}

    public static final ThreadRegistry THREADS = new ThreadRegistry();
    public static final LockRegistry LOCKS = new LockRegistry();
    public static final WaitingRegistry WAITING = new WaitingRegistry();

    public static final WaitForGraph GRAPH = new WaitForGraph();

    public static final DeadlockDetector DETECTOR =
            new DeadlockDetector();

    public static final GraphEngine ENGINE =
            new GraphEngine(
                    THREADS,
                    LOCKS,
                    WAITING,
                    GRAPH,
                    DETECTOR
            );
}