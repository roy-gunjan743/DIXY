package com.virtual_deadlock_detection.graph.detector;

import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Specifically analyzes the graph for virtual thread pinning issues,
 * where carrier threads are starved due to pinned virtual threads.
 */
public class PinningDetector {

    private final DeadlockDetector baseDetector;

    public PinningDetector(DeadlockDetector baseDetector) {
        this.baseDetector = baseDetector;
    }

    /**
     * Finds deadlocks specifically caused by virtual thread pinning.
     */
    public List<DeadlockCycle> detectPinningDeadlocks(WaitForGraph graph) {
        List<DeadlockCycle> allCycles = baseDetector.detect(graph);

        // Filter out standard lock deadlocks, returning only cycles
        // that include at least one PINS edge.
        return allCycles.stream()
                .filter(DeadlockCycle::isPinningCycle)
                .collect(Collectors.toList());
    }
}