package com.virtual_deadlock_detection.agent;

import com.virtual_deadlock_detection.collector.EventCollector;
import com.virtual_deadlock_detection.jfr.RecordingManager;
import com.virtual_deadlock_detection.jvmti.JvmtiBridge;

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

        // Later we'll inject the collector into RecordingManager
        // recordingManager.setCollector(collector);

        recordingManager.startRecording();

        JvmtiBridge jvmtiBridge = new JvmtiBridge();
        jvmtiBridge.initialize();

        // Run deadlock detection on the current graph


        System.out.println("Agent Initialization Completed");
    }
}