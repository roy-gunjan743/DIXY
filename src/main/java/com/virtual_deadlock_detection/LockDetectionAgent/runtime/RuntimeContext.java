package com.virtual_deadlock_detection.LockDetectionAgent.runtime;

import com.virtual_deadlock_detection.graph.detector.DeadlockDetector;
import com.virtual_deadlock_detection.graph.engine.GraphEngine;
import com.virtual_deadlock_detection.graph.registry.*;

public final class RuntimeContext {

    private RuntimeContext() {}

    public static final ThreadRegistry THREADS = new ThreadRegistry();

    public static final LockRegistry LOCKS = new LockRegistry();

    public static final WaitingRegistry WAITING = new WaitingRegistry();

    public static final WaitForGraph GRAPH = new WaitForGraph();

    public static final DeadlockDetector DETECTOR =
            new DeadlockDetector();

    public static final GraphEngine ENGINE =
            new GraphEngine(
                    THREADS,
                    LOCKS,
                    WAITING,
                    GRAPH,
                    DETECTOR
            );

    /**
     * Configuration supplied by -javaagent arguments.
     *
     * Example:
     *
     * serverUrl=http://localhost:8081
     * apiKey=dixy-local-dev-key-123
     * appName=deadlock-lab
     */
    private static volatile String serverUrl =
            "http://localhost:8081";

    private static volatile String apiKey = "";

    private static volatile String appName =
            "unknown-app";

    /**
     * Configures the shared agent runtime.
     */
    public static void configure(
            String serverUrl,
            String apiKey,
            String appName) {

        if (serverUrl != null && !serverUrl.isBlank()) {
            RuntimeContext.serverUrl =
                    removeTrailingSlash(serverUrl);
        }

        if (apiKey != null) {
            RuntimeContext.apiKey = apiKey;
        }

        if (appName != null && !appName.isBlank()) {
            RuntimeContext.appName = appName;
        }
    }

    public static String getServerUrl() {
        return serverUrl;
    }

    public static String getApiKey() {
        return apiKey;
    }

    public static String getAppName() {
        return appName;
    }

    /**
     * Removes trailing '/' characters so callers can safely append
     * API paths.
     */
    private static String removeTrailingSlash(
            String value) {

        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }

    /**
     * No-op by default.
     *
     * VirtualDeadlockAgent replaces this with RemoteReportSink
     * during agent startup.
     */
    public static volatile DeadlockReportSink REPORT_SINK =
            cycle -> {
            };
}