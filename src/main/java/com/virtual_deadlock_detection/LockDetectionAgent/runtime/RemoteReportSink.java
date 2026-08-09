package com.virtual_deadlock_detection.LockDetectionAgent.runtime;

import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.LockDetectionAgent.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

/**
 * {@link DeadlockReportSink} for the standalone -javaagent case (see
 * VirtualDeadlockAgent.premain). There's no Spring context in that JVM to
 * persist a cycle locally, so instead this posts the same JSON shape the
 * backend's own DetectionPipelineLifecycle.persistCycle() builds to the
 * existing ingestion endpoint: POST {serverUrl}/api/internal/deadlocks,
 * matching AgentDeadlockDTO / AgentDeadlockDTO.ThreadRef exactly.
 *
 * Deliberately dependency-free (no Jackson) so it fits in the slim
 * agentRuntime jar — JSON is hand-built since the DTO shape is small and
 * fixed.
 */
public class RemoteReportSink implements DeadlockReportSink {

    private static final Logger log = Logger.getLogger(RemoteReportSink.class);

    private final String endpoint;
    private final String appName;
    private final String instanceId;
    private final String apiKey;
    private final HttpClient httpClient;

    public RemoteReportSink(String serverUrl, String appName, String apiKey) {
        String base = (serverUrl == null || serverUrl.isBlank())
                ? "http://localhost:8080"
                : serverUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/api/internal/deadlocks";
        this.appName = (appName == null || appName.isBlank()) ? "unknown-app" : appName;
        this.instanceId = "pid-" + ProcessHandle.current().pid();
        // Required: SecurityConfig's ApiKeyAuthenticationFilter rejects any
        // request without a matching X-API-Key header on this route — it's
        // not on the permitAll() allowlist (only /actuator/health and
        // static assets are). A missing/wrong key means every report gets
        // a silent 401 unless callers check for it, hence the warn below.
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No apiKey supplied to RemoteReportSink — the DIXY backend will reject "
                    + "reports to {} with 401 Unauthorized unless security.api-key is disabled server-side. "
                    + "Pass apiKey=... in the -javaagent options.", endpoint);
        }
    }

    @Override
    public void report(DeadlockCycle cycle) {
        String json = toJson(cycle);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-API-Key", apiKey);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

        // Fire-and-forget: never let a reporting failure affect the
        // instrumented application. Logged at warn so it's visible without
        // needing debug logging enabled.
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.warn("Failed to report deadlock cycle to {}: {}", endpoint, error.toString());
                    } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                        log.warn("Deadlock report to {} rejected with HTTP {} — check that apiKey= matches "
                                + "the backend's security.api-key.", endpoint, response.statusCode());
                    } else if (response.statusCode() >= 300) {
                        log.warn("Deadlock report to {} returned HTTP {}", endpoint, response.statusCode());
                    } else {
                        log.info("Reported deadlock cycle to {}", endpoint);
                    }
                });
    }

    private String toJson(DeadlockCycle cycle) {
        List<GraphNode> threadNodes = cycle.getCycle().stream()
                .filter(GraphNode::isThread)
                .toList();

        StringJoiner threadsJson = new StringJoiner(",", "[", "]");
        for (GraphNode node : threadNodes) {
            threadsJson.add("{"
                    + "\"id\":" + node.getId() + ","
                    + "\"name\":\"" + escape(node.getLabel()) + "\""
                    + "}");
        }

        return "{"
                + "\"severity\":\"" + (cycle.isPinningCycle() ? "CRITICAL" : "HIGH") + "\","
                + "\"analysisTimeMicros\":0,"
                + "\"appName\":\"" + escape(appName) + "\","
                + "\"instanceId\":\"" + escape(instanceId) + "\","
                + "\"detectionStrategy\":\"" + escape(cycle.getDetectionStrategy()) + "\","
                + "\"threads\":" + threadsJson
                + "}";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}