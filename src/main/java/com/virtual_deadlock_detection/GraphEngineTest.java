package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.report.DeadlockReport;
import com.virtual_deadlock_detection.graph.report.DeadlockReportGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphEngineTest {

    public static void main(String[] args) {
        System.out.println(" Starting Deadlock Detection Test...\n");

        // 1. Initialize the Registry and Detectors
        WaitForGraph graph = new WaitForGraph();
        DeadlockDetector detector = new DeadlockDetector();
        DeadlockReportGenerator reportGenerator = new DeadlockReportGenerator();

        // 2. Create Dummy Nodes
        GraphNode thread1 = new GraphNode(101, NodeType.VIRTUAL_THREAD, "vThread-1");
        GraphNode thread2 = new GraphNode(102, NodeType.VIRTUAL_THREAD, "vThread-2");
        GraphNode lockA = new GraphNode(201, NodeType.LOCK, "ReentrantLock-A");
        GraphNode lockB = new GraphNode(202, NodeType.LOCK, "ReentrantLock-B");

        // 3. Simulate a Deadlock (T1 holds A, waits for B. T2 holds B, waits for A)
        graph.addEdge(new GraphEdge(lockA, thread1, EdgeType.HELD_BY));   // Thread 1 holds Lock A
        graph.addEdge(new GraphEdge(thread1, lockB, EdgeType.WAITS_FOR)); // Thread 1 wants Lock B

        graph.addEdge(new GraphEdge(lockB, thread2, EdgeType.HELD_BY));   // Thread 2 holds Lock B
        graph.addEdge(new GraphEdge(thread2, lockA, EdgeType.WAITS_FOR)); // Thread 2 wants Lock A

        // 4. Run the Detector
        List<DeadlockCycle> detectedCycles = detector.detect(graph);

        // 5. Generate and Print the Report
        if (detectedCycles.isEmpty()) {
            System.out.println(" No deadlocks detected.");
        } else {
            // Mock a stack lookup for the report generator
            Map<GraphNode, DeadlockReport.StackFrame> mockStacks = new HashMap<>();
            mockStacks.put(thread1, new DeadlockReport.StackFrame("vThread-1", "com.example.BillingService", "processPayment", 42));
            mockStacks.put(thread2, new DeadlockReport.StackFrame("vThread-2", "com.example.InventoryService", "updateStock", 88));

            for (DeadlockCycle cycle : detectedCycles) {
                DeadlockReport report = reportGenerator.generate(cycle, mockStacks);
                System.out.println(reportGenerator.renderConsoleAlert(report));
            }
        }
    }
}