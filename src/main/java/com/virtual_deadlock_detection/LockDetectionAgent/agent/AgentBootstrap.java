package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.engine.GraphEngine;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

public class AgentBootstrap {

    private final EventCollector eventCollector;

    public AgentBootstrap() {

        System.out.println("=================================");
        System.out.println("Initializing Agent Components");
        System.out.println("=================================");

        eventCollector = new EventCollector();

        System.out.println("Agent Components Initialized Successfully");
    }

    public EventCollector getEventCollector() {
        return eventCollector;
    }

    // Was: EventSender.getGraph(). Now points at the one shared graph.
    public WaitForGraph getGraph() {
        return RuntimeContext.GRAPH;
    }

    // Was: its own private `new DeadlockDetector()`. Now the shared instance
    // (harmless either way since DeadlockDetector is stateless, but this
    // keeps one source of truth as the codebase grows).
    public DeadlockDetector getDetector() {
        return RuntimeContext.DETECTOR;
    }

    // Was: RuntimeContext.engine() — ambiguous, resolved to the now-deleted
    // agent.RuntimeContext duplicate. Now explicit.
    public GraphEngine getGraphEngine() {
        return RuntimeContext.ENGINE;
    }
}