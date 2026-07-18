package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.JvmtiBridge;

import java.lang.instrument.Instrumentation;

public class VirtualDeadlockAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        System.out.println("======================================");
        System.out.println(" Virtual Deadlock Detection Agent");
        System.out.println(" Agent Started Successfully");
        System.out.println("======================================");

        System.out.println("Loaded Classes : " + inst.getAllLoadedClasses().length);

        AgentBootstrap bootstrap = new AgentBootstrap();

        EventCollector collector = bootstrap.getEventCollector();

        RecordingManager recordingManager = new RecordingManager();

        // Was: commented out. Now wired — JFR and JVMTI share one collector.
        recordingManager.setCollector(collector);

        recordingManager.startRecording();

        JvmtiBridge jvmtiBridge = new JvmtiBridge();
        jvmtiBridge.initialize();

        System.out.println("Agent Initialization Completed");
    }
}