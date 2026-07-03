package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphService {

    private final WaitForGraph waitForGraph;
    private final DeadlockDetector detector;

    public GraphService(WaitForGraph waitForGraph) {
        this.waitForGraph = waitForGraph;
        this.detector = new DeadlockDetector();
    }

    public List<DeadlockCycle> detectDeadlocks() {
        return detector.detect(waitForGraph);
    }
}