package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.engine.GraphEngine;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.LockDetectionAgent.sender.EventSender;

public class AgentBootstrap {

    private final EventCollector eventCollector;

    private final DeadlockDetector detector;

    private final GraphEngine graphEngine;

    public AgentBootstrap() {

        System.out.println("=================================");
        System.out.println("Initializing Agent Components");
        System.out.println("=================================");

        eventCollector = new EventCollector();

        detector = new DeadlockDetector();

        graphEngine = RuntimeContext.engine();

        System.out.println("Agent Components Initialized Successfully");
    }

    public EventCollector getEventCollector() {
        return eventCollector;
    }

    public WaitForGraph getGraph() {
        return EventSender.getGraph();
    }

    public DeadlockDetector getDetector() {
        return detector;
    }

    public GraphEngine getGraphEngine() {
        return graphEngine;
    }
}