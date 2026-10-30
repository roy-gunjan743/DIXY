package com.virtual_deadlock_detection.LockDetectionAgent.agent;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import com.virtual_deadlock_detection.LockDetectionAgent.jfr.RecordingManager;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.JvmtiBridge;
import com.virtual_deadlock_detection.LockDetectionAgent.monitor.ThreadStatePoller;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RemoteReportSink;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class VirtualDeadlockAgent {

    private static final AtomicBoolean STARTED = new AtomicBoolean();

    public static void premain(String agentArgs, Instrumentation inst) {

        start(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        start(agentArgs, inst);
    }

    private static void start(String agentArgs, Instrumentation inst) {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        System.out.println("======================================");
        System.out.println(" Virtual Deadlock Detection Agent");
        System.out.println(" Agent Started Successfully");
        System.out.println("======================================");

        System.out.println("Loaded Classes : " + inst.getAllLoadedClasses().length);

        Map<String, String> options = parseAgentArgs(agentArgs);
        String serverUrl = options.getOrDefault("serverUrl", "http://localhost:8080");
        String appName = options.getOrDefault("appName", "unknown-app");
        String apiKey = options.get("apiKey");
        RuntimeContext.configure(
                serverUrl,
                apiKey,
                appName
        );

        // Wire the shared graph's report sink so detected cycles get pushed
        // to the DIXY backend's existing /api/internal/deadlocks endpoint —
        // there's no Spring context in this (the target app's) JVM to
        // persist locally, unlike DetectionPipelineLifecycle's in-app case.
        RuntimeContext.REPORT_SINK = new RemoteReportSink(serverUrl, appName, apiKey);
        System.out.println("Reporting detected deadlocks to: " + serverUrl + "/api/internal/deadlocks");
        System.out.println("Reporting as appName: " + appName);
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("WARNING: no apiKey= supplied — the backend will reject reports with 401 "
                    + "unless security.api-key is disabled server-side.");
        }

        AgentBootstrap bootstrap = new AgentBootstrap();

        EventCollector collector = bootstrap.getEventCollector();

        RecordingManager recordingManager = new RecordingManager();

        // Was: commented out. Now wired — JFR and JVMTI share one collector.
        recordingManager.setCollector(collector);

        recordingManager.startRecording();


        // NEW — JFR alone can't see permanent blocks (see ThreadStatePoller javadoc).
        ThreadStatePoller poller = new ThreadStatePoller(collector);
        poller.start(250);

        JvmtiBridge jvmtiBridge = new JvmtiBridge();
        jvmtiBridge.initialize();

        System.out.println("Agent Initialization Completed");
    }

    /**
     * Parses the -javaagent:agent.jar=key=value,key=value string.
     * Supported keys: serverUrl (DIXY backend base URL), appName (label
     * shown on the dashboard for this instrumented application), apiKey
     * (must match the backend's security.api-key, or reports get 401'd).
     * Example: -javaagent:agent.jar=serverUrl=http://localhost:8080,apiKey=YOUR-KEY,appName=my-service
     */
    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> options = new HashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return options;
        }
        for (String pair : agentArgs.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    options.put(key, value);
                }
            }
        }
        return options;
    }
}