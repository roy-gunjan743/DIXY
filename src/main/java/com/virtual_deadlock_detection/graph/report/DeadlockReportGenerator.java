package com.virtual_deadlock_detection.graph.report;

import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates a {@link DeadlockReport} from a confirmed {@link DeadlockCycle}.
 *
 * Per the design doc's Phase 5 (Non-Blocking Telemetry Extraction), only
 * the specific threads involved in the cycle get their stack traces
 * pulled - never a global "stop the world" dump. Callers supply a
 * stack-lookup function (backed by JVMTI's targeted stack trace API in
 * production) so this class stays free of any native/JVMTI dependency
 * and is trivially unit-testable.
 */
@Component
public class DeadlockReportGenerator {

    /**
     * @param cycle       the confirmed cycle from the detector
     * @param stackLookup function providing the top stack frame for a given thread node
     */
    public DeadlockReport generate(DeadlockCycle cycle,
                                   Map<GraphNode, DeadlockReport.StackFrame> stackLookup) {
        List<DeadlockReport.StackFrame> frames = new ArrayList<>();
        for (GraphNode node : cycle.getCycle()) {
            if (node.isThread()) {
                DeadlockReport.StackFrame frame = stackLookup.get(node);
                if (frame != null) {
                    frames.add(frame);
                }
            }
        }
        return DeadlockReport.of(cycle.getDetectionStrategy(), cycle.getCycle(), frames);
    }

    /** Human-readable console alert, matching the doc's example output format. */
    public String renderConsoleAlert(DeadlockReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(" DEADLOCK DETECTED [").append(report.severity()).append("]");
        sb.append("Report ID: ").append(report.reportId()).append("\n");
        sb.append("Detected: ").append(report.detectedAt()).append(" via ").append(report.detectionStrategy()).append("\n");
        sb.append("Cycle: ").append(String.join(" -> ", report.cycleNodeLabels()))
                .append(" -> ").append(report.cycleNodeLabels().isEmpty() ? "" : report.cycleNodeLabels().get(0)).append("\n");
        for (DeadlockReport.StackFrame frame : report.stackTargets()) {
            sb.append("\tat ").append(frame).append("\n");
        }
        return sb.toString();
    }
}