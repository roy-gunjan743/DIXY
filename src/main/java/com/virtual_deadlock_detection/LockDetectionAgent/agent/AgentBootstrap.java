package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.NativeEventBridge;
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

        /*
         * Create the ONE shared EventCollector.
         */
        eventCollector = new EventCollector();

        /*
         * IMPORTANT:
         * Connect the JVMTI bridge immediately.
         *
         * JVMTI can start generating events very early during JVM
         * startup, so this MUST happen immediately after creating
         * the EventCollector and BEFORE JVMTI initialization.
         */
        NativeEventBridge.setCollector(eventCollector);

        System.out.println(
                "NativeEventBridge connected to shared EventCollector"
        );

        System.out.println(
                "Agent Components Initialized Successfully"
        );
    }

    public EventCollector getEventCollector() {
        return eventCollector;
    }

    /*
     * Shared WaitForGraph.
     */
    public WaitForGraph getGraph() {
        return RuntimeContext.GRAPH;
    }

    /*
     * Shared DeadlockDetector.
     */
    public DeadlockDetector getDetector() {
        return RuntimeContext.DETECTOR;
    }

    /*
     * Shared GraphEngine.
     */
    public GraphEngine getGraphEngine() {
        return RuntimeContext.ENGINE;
    }
}