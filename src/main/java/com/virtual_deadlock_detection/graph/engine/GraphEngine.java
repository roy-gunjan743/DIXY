package com.virtual_deadlock_detection.graph.engine;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import java.util.List;

public class GraphEngine {

    private final WaitForGraph graph;
    private final DeadlockDetector detector;

    public GraphEngine(WaitForGraph graph) {
        this.graph = graph;
        this.detector = new DeadlockDetector();
    }

    public void analyze() {

        List<DeadlockCycle> cycles = detector.detect(graph);

        System.out.println("==================================");
        System.out.println("Deadlock Analysis");
        System.out.println("Cycles Found : " + cycles.size());
        System.out.println("==================================");

        for (DeadlockCycle cycle : cycles) {
            System.out.println(cycle);
        }
    }
}