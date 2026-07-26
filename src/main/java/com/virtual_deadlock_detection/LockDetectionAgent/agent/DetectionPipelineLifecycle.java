package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.JvmtiBridge;
import com.virtual_deadlock_detection.LockDetectionAgent.monitor.ThreadStatePoller;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class DetectionPipelineLifecycle {

    private RecordingManager recordingManager;
    private ThreadStatePoller poller;
    private JvmtiBridge jvmtiBridge;

    @PostConstruct
    public void start() {
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
}
