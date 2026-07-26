package com.virtual_deadlock_detection.graph.model;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a detected circular dependency in the Wait-For Graph.
 */
public final class DeadlockCycle {

    private final List<GraphEdge> edgesInCycle;
    private final String detectionStrategy;
    private final List<GraphEdge> correlatedPinningEdges;

    public DeadlockCycle(List<GraphEdge> edgesInCycle, String detectionStrategy) {
        this(edgesInCycle, detectionStrategy, Collections.emptyList());
    }

    // NEW overload — correlatedPinningEdges are PINS edges sourced from a
    // node that's part of this cycle but NOT themselves part of the cycle's
    // own edge path (see DeadlockDetector.correlatePinning).
    public DeadlockCycle(List<GraphEdge> edgesInCycle, String detectionStrategy,
                         List<GraphEdge> correlatedPinningEdges) {
        this.edgesInCycle = List.copyOf(edgesInCycle);
        this.detectionStrategy = detectionStrategy != null ? detectionStrategy : "UNKNOWN";
        this.correlatedPinningEdges = List.copyOf(correlatedPinningEdges);
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

    /**
     * PINS edges sourced from a node inside this cycle, aged past the
     * correlation threshold — i.e., a thread involved in this deadlock was
     * ALSO pinned around the time the deadlock was detected. This is the
     * unified-detection signal: the core patentable claim.
     */
    public List<GraphEdge> getCorrelatedPinningEdges() {
        return correlatedPinningEdges;
    }

    public boolean hasPinningCorrelation() {
        return !correlatedPinningEdges.isEmpty();
    }

    /**
     * True if a PINS edge is literally inside the cycle's own edge path
     * (structurally rare — would require carrier-scheduling telemetry the
     * JDK doesn't expose publicly) OR if a PINS edge is correlated with a
     * node in this cycle (the realistic, observable signal).
     */
    public boolean isPinningCycle() {
        return edgesInCycle.stream().anyMatch(edge -> edge.getType() == EdgeType.PINS)
                || hasPinningCorrelation();
    }

    @Override
    public String toString() {
        if (edgesInCycle.isEmpty()) return "Empty Cycle";

        String prefix = isPinningCycle() ? "Pinning-Correlated Deadlock Cycle:" : "Standard Deadlock Cycle:";
        String cyclePath = edgesInCycle.stream()
                .map(GraphEdge::toString)
                .collect(Collectors.joining("\n  -> "));

        StringBuilder sb = new StringBuilder(prefix).append("\n  -> ").append(cyclePath);

        if (hasPinningCorrelation()) {
            sb.append("\n  [Correlated pinning — a thread in this cycle was ALSO pinned]:");
            for (GraphEdge pin : correlatedPinningEdges) {
                sb.append("\n    -> ").append(pin);
            }
        }

        return sb.toString();
    }
}