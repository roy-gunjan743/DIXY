
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
    private static final AtomicBoolean STOPPED = new AtomicBoolean();

    private static EventCollector collector;
    private static RecordingManager recordingManager;
    private static ThreadStatePoller poller;
    private static JvmtiBridge jvmtiBridge;

    public static void premain(String agentArgs, Instrumentation inst) {
        start(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        start(agentArgs, inst);
    }

    private static void start(
            String agentArgs,
            Instrumentation inst) {

        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        System.out.println("======================================");
        System.out.println(" Virtual Deadlock Detection Agent");
        System.out.println(" Agent Started Successfully");
        System.out.println("======================================");

        System.out.println(
                "Loaded Classes : " + inst.getAllLoadedClasses().length
        );

        Map<String, String> options = parseAgentArgs(agentArgs);

        String serverUrl = options.getOrDefault(
                "serverUrl",
                "http://localhost:8080"
        );

        String appName = options.getOrDefault(
                "appName",
                "unknown-app"
        );

        String apiKey = options.get("apiKey");

        RuntimeContext.configure(serverUrl, apiKey, appName);

        RuntimeContext.REPORT_SINK =
                new RemoteReportSink(serverUrl, appName, apiKey);

        System.out.println(
                "Reporting detected deadlocks to: "
                        + serverUrl + "/api/internal/deadlocks"
        );

        System.out.println("Reporting as appName: " + appName);

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println(
                    "WARNING: No API key supplied. Backend may reject requests."
            );
        }

        AgentBootstrap bootstrap = new AgentBootstrap();

        collector = bootstrap.getEventCollector();

        recordingManager = new RecordingManager();
        recordingManager.setCollector(collector);
        recordingManager.startRecording();

        poller = new ThreadStatePoller(collector);
        poller.start(250);

        jvmtiBridge = new JvmtiBridge();
        jvmtiBridge.initialize();

        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        VirtualDeadlockAgent::shutdown,
                        "dixy-agent-shutdown"
                )
        );

        System.out.println("Agent Initialization Completed");
    }

    /**
     * Stops collection and notifies the DIXY backend.
     */
    private static void shutdown() {

        if (!STOPPED.compareAndSet(false, true)) {
            return;
        }

        System.out.println("Stopping DIXY monitoring agent...");

        try {

            // Stop new polling events first.
            if (poller != null) {
                poller.stop();
            }

            // Stop JFR event recording.
            if (recordingManager != null) {
                recordingManager.stopRecording();
            }

            // Stop native JVMTI event collection.
            if (jvmtiBridge != null) {
                jvmtiBridge.shutdown();
            }

            // Notify backend after stopping event producers.
            if (collector != null) {
                collector.stopMonitoring();
            }

        } catch (Exception e) {

            System.err.println(
                    "DIXY shutdown notification failed: "
                            + e.getMessage()
            );
        }

        System.out.println("DIXY monitoring agent stopped.");
    }

    /**
     * Parses -javaagent arguments.
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
