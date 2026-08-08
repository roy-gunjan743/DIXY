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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DetectionPipelineLifecycle {

    private final AgentDeadlockService agentDeadlockService;

    private RecordingManager recordingManager;
    private ThreadStatePoller poller;
    private JvmtiBridge jvmtiBridge;

    public DetectionPipelineLifecycle(AgentDeadlockService agentDeadlockService) {
        this.agentDeadlockService = agentDeadlockService;
    }

    @PostConstruct
    public void start() {
        RuntimeContext.REPORT_SINK = this::persistCycle;
        System.out.println("======================================");
        System.out.println(" Starting Virtual Deadlock Detection Pipeline");
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
