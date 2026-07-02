package com.virtual_deadlock_detection.graph.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a detected circular dependency in the Wait-For Graph.
 */
public final class DeadlockCycle {

    private final List<GraphEdge> edgesInCycle;
    private final String detectionStrategy;

    public DeadlockCycle(List<GraphEdge> edgesInCycle, String detectionStrategy) {
        this.edgesInCycle = List.copyOf(edgesInCycle);
        this.detectionStrategy = detectionStrategy != null ? detectionStrategy : "UNKNOWN";
    }

    /** Required by DeadlockReportGenerator.java */
    public String getDetectionStrategy() {
        return detectionStrategy;
    }

    /** Required by DeadlockReportGenerator.java
     * Extracts just the source nodes in order to map out the cycle.
     */
    public List<GraphNode> getCycle() {
        return edgesInCycle.stream()
                .map(GraphEdge::getSource)
                .collect(Collectors.toList());
    }

    public List<GraphEdge> getEdgesInCycle() {
        return edgesInCycle;
    }

    public boolean isPinningCycle() {
        return edgesInCycle.stream()
                .anyMatch(edge -> edge.getType() == EdgeType.PINS);
    }

    @Override
    public String toString() {
        if (edgesInCycle.isEmpty()) return "Empty Cycle";

        String prefix = isPinningCycle() ? "Pinning Deadlock Cycle:" : "Standard Deadlock Cycle:";
        String cyclePath = edgesInCycle.stream()
                .map(GraphEdge::toString)
                .collect(Collectors.joining("\n  -> "));

        return prefix + "\n  -> " + cyclePath;
    }
}