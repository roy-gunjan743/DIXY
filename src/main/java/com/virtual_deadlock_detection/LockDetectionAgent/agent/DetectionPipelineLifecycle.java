package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.JvmtiBridge;
import com.virtual_deadlock_detection.LockDetectionAgent.monitor.ThreadStatePoller;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.backend.dto.AgentDeadlockDTO;
import com.virtual_deadlock_detection.backend.service.AgentDeadlockService;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IMPORTANT: this used to start unconditionally for every profile (web, cli,
 * local) via @PostConstruct. That means the app self-attaches its own JVMTI
 * monitor-contention hooks to the very JVM it's running in, and starts
 * watching its own internal threads (Spring/JFR/GC infrastructure).
 *
 * That caused a self-monitoring feedback loop: JVMTI reports a lock event
 * on "main" or JFR's own "JFR Event Stream 1" thread -> the Java-side
 * callback (NativeEventBridge.publishLockOwned) itself acquires locks while
 * handling that event (a synchronized method, SLF4J, collections) -> JVMTI
 * sees *those* lock acquisitions and fires the callback again, recursively,
 * with no way to stop -> the JVM eventually throws in both "main" and
 * "JFR Event Stream 1" before the CLI's interactive shell ever gets a
 * chance to start, which looks like "the CLI isn't showing".
 *
 * This -javaagent was designed to attach to a *separate target* JVM (see
 * the agentJar task in build.gradle), not to monitor the app hosting it.
 * Self-monitoring is now opt-in and off by default.
 */
@Component
public class DetectionPipelineLifecycle {

    private final AgentDeadlockService agentDeadlockService;

    @Value("${agent.self-monitoring.enabled:false}")
    private boolean selfMonitoringEnabled;

    private RecordingManager recordingManager;
    private ThreadStatePoller poller;
    private JvmtiBridge jvmtiBridge;

    public DetectionPipelineLifecycle(AgentDeadlockService agentDeadlockService) {
        this.agentDeadlockService = agentDeadlockService;
    }

    @PostConstruct
    public void start() {
        if (!selfMonitoringEnabled) {
            return;
        }

        RuntimeContext.REPORT_SINK = this::persistCycle;
        System.out.println("======================================");
        System.out.println(" Starting Virtual Deadlock Detection Pipeline");
        System.out.println(" (self-monitoring — for demo/debugging only)");
        System.out.println("======================================");

        AgentBootstrap bootstrap = new AgentBootstrap();
        EventCollector collector = bootstrap.getEventCollector();

        recordingManager = new RecordingManager();
        recordingManager.setCollector(collector);
        recordingManager.startRecording();

        poller = new ThreadStatePoller(collector);
        poller.start(250);

        try {
            jvmtiBridge = new JvmtiBridge();
            jvmtiBridge.initialize();
        } catch (UnsatisfiedLinkError | SecurityException ex) {
            System.err.println("JVMTI native bridge unavailable; continuing with JFR and ThreadMXBean polling: "
                    + ex.getMessage());
        } catch (RuntimeException ex) {
            System.err.println("JVMTI native bridge failed to initialize; continuing with JFR and ThreadMXBean polling: "
                    + ex.getMessage());
        }

        System.out.println("Virtual Deadlock Detection Pipeline Started");
    }

    @PreDestroy
    public void stop() {
        if (!selfMonitoringEnabled) {
            return;
        }
        if (poller != null) {
            poller.stop();
        }
        if (recordingManager != null) {
            recordingManager.stopRecording();
        }
        if (jvmtiBridge != null) {
            jvmtiBridge.shutdown();
        }
    }
    private void persistCycle(DeadlockCycle cycle) {
        AgentDeadlockDTO dto = new AgentDeadlockDTO();
        dto.setSeverity(cycle.isPinningCycle() ? "CRITICAL" : "HIGH");
        dto.setAnalysisTimeMicros(0L);
        dto.setAppName("virtual_deadlock_detection");
        dto.setInstanceId("pid-" + ProcessHandle.current().pid());
        dto.setDetectionStrategy(cycle.getDetectionStrategy());

        List<AgentDeadlockDTO.ThreadRef> threads = new ArrayList<>();
        for (GraphNode node : cycle.getCycle()) {
            if (!node.isThread()) continue;
            AgentDeadlockDTO.ThreadRef ref = new AgentDeadlockDTO.ThreadRef();
            ref.setId(node.getId());
            ref.setName(node.getLabel());
            threads.add(ref);
        }
        dto.setThreads(threads);

        agentDeadlockService.save(dto);
    }
}