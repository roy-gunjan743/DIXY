package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.agent.AgentConfig;
import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.graph.report.DeadlockReport;
import com.virtual_deadlock_detection.graph.report.DeadlockReportGenerator;
import com.virtual_deadlock_detection.jfr.RecordingManager;
import com.virtual_deadlock_detection.jvmti.JvmtiBridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class GraphEngineTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println(" Starting REAL Virtual Thread Deadlock Integration Test...\n");

        // --- 1. BOOTSTRAP THE INTAKE PIPELINE (The "Ears") ---
        System.out.println(">>> [AGENT BOOTSTRAP PHASE] <<<");
        AgentConfig config = new AgentConfig();

        RecordingManager recordingManager = null;
        if (config.isJfrEnabled()) {
            recordingManager = new RecordingManager();
            recordingManager.startRecording(); // Starts JFR stream to listen for Thread events
        }

        JvmtiBridge jvmtiBridge = null;
        if (config.isJvmtiEnabled()) {
            jvmtiBridge = new JvmtiBridge();
            jvmtiBridge.initialize(); // Prepares Native Library
        }
        System.out.println(">>> [AGENT BOOTSTRAP COMPLETE] <<<\n");

        // --- 2. Initialize the Graph Engine (The "Brain") ---
        WaitForGraph graph = new WaitForGraph();
        DeadlockDetector detector = new DeadlockDetector();
        DeadlockReportGenerator reportGenerator = new DeadlockReportGenerator();

        // Create Real Java Locks
        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();

        // Create Graph Nodes to represent them
        GraphNode nodeLockA = new GraphNode(201, NodeType.LOCK, "ReentrantLock-A");
        GraphNode nodeLockB = new GraphNode(202, NodeType.LOCK, "ReentrantLock-B");
        GraphNode nodeThread1 = new GraphNode(101, NodeType.VIRTUAL_THREAD, "vThread-1");
        GraphNode nodeThread2 = new GraphNode(102, NodeType.VIRTUAL_THREAD, "vThread-2");

        // --- 3. Spawn Real Virtual Threads ---
        // Note: Right now, we still manually call graph.addEdge().
        // In the next phase, your JfrEventListener/EventCollector will do this automatically!

        Thread vThread1 = Thread.ofVirtual().name("vThread-1").start(() -> {
            lockA.lock();
            graph.addEdge(new GraphEdge(nodeLockA, nodeThread1, EdgeType.HELD_BY));
            System.out.println(" vThread-1 acquired Lock A");

            sleepBriefly();

            System.out.println(" vThread-1 is waiting for Lock B...");
            graph.addEdge(new GraphEdge(nodeThread1, nodeLockB, EdgeType.WAITS_FOR));
            lockB.lock(); //  IT GETS STUCK HERE FOREVER
        });

        Thread vThread2 = Thread.ofVirtual().name("vThread-2").start(() -> {
            lockB.lock();
            graph.addEdge(new GraphEdge(nodeLockB, nodeThread2, EdgeType.HELD_BY));
            System.out.println(" vThread-2 acquired Lock B");

            sleepBriefly();

            System.out.println("🧵 vThread-2 is waiting for Lock A...");
            graph.addEdge(new GraphEdge(nodeThread2, nodeLockA, EdgeType.WAITS_FOR));
            lockA.lock(); //  IT GETS STUCK HERE FOREVER
        });

        // --- 4. Spawn a "Watchdog" Thread to monitor the graph ---
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(2000); // Give the threads 2 seconds to deadlock
                System.out.println("\n Watchdog is scanning the graph...");

                List<DeadlockCycle> cycles = detector.detect(graph);

                if (cycles.isEmpty()) {
                    System.out.println(" No deadlocks detected.");
                } else {
                    Map<GraphNode, DeadlockReport.StackFrame> mockStacks = new HashMap<>();
                    mockStacks.put(nodeThread1, new DeadlockReport.StackFrame("vThread-1", "com.example.Service", "methodA", 42));
                    mockStacks.put(nodeThread2, new DeadlockReport.StackFrame("vThread-2", "com.example.Service", "methodB", 88));

                    for (DeadlockCycle cycle : cycles) {
                        DeadlockReport report = reportGenerator.generate(cycle, mockStacks);
                        System.out.println("\n" + reportGenerator.renderConsoleAlert(report));
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        watchdog.start();
        watchdog.join(); // Keep main alive

        // --- 5. Cleanup Agent Resources ---
        if (recordingManager != null) {
            recordingManager.stopRecording();
        }
        if (jvmtiBridge != null) {
            jvmtiBridge.shutdown();
        }
    }

    private static void sleepBriefly() {
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
    }
}