package com.virtual_deadlock_detection.graph.report;

import com.virtual_deadlock_detection.graph.model.GraphNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured, serializable report for one confirmed deadlock (or pinning
 * incident). This is the payload the pipeline emits as JSON log / JMX
 * metric / console alert (Phase 5 in the design doc).
 */
public record DeadlockReport(
        String reportId,
        Instant detectedAt,
        String detectionStrategy,
        List<String> cycleNodeLabels,
        List<StackFrame> stackTargets,
        String severity
) {

    public static DeadlockReport of(String detectionStrategy, List<GraphNode> cycle,
                                    List<StackFrame> stackTargets) {
        return new DeadlockReport(
                UUID.randomUUID().toString(),
                Instant.now(),
                detectionStrategy,
                cycle.stream().map(GraphNode::getLabel).toList(),
                stackTargets,
                cycle.size() > 4 ? "CRITICAL" : "HIGH"
        );
    }

    public record StackFrame(String threadLabel, String className, String methodName, int lineNumber) {
        @Override
        public String toString() {
            return threadLabel + " at " + className + "." + methodName + ":" + lineNumber;
        }
    }
}