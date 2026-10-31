
package com.virtual_deadlock_detection.LockDetectionAgent.sender;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.integration.GraphGateway;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class EventSender {

    private static final Logger log =
            LoggerFactory.getLogger(EventSender.class);

    private static final String DEFAULT_SERVER_URL =
            "http://localhost:8081";

    private static final String DEFAULT_API_KEY =
            "dixy-local-dev-key-123";

    private static final String DEFAULT_APP_NAME =
            "unknown-app";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(3);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String telemetryEndpoint;
    private final String apiKey;
    private final String appName;
    private final String instanceId;
    private final GraphGateway graphGateway;

    public EventSender() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        this.graphGateway = new GraphGateway(RuntimeContext.GRAPH);

        String serverUrl = RuntimeContext.getServerUrl();

        if (serverUrl == null || serverUrl.isBlank()) {
            serverUrl = DEFAULT_SERVER_URL;
        }

        serverUrl = removeTrailingSlash(serverUrl);

        this.telemetryEndpoint = serverUrl + "/api/telemetry";

        String configuredApiKey = RuntimeContext.getApiKey();

        this.apiKey =
                configuredApiKey == null || configuredApiKey.isBlank()
                        ? DEFAULT_API_KEY
                        : configuredApiKey;

        String configuredAppName = RuntimeContext.getAppName();

        this.appName =
                configuredAppName == null || configuredAppName.isBlank()
                        ? DEFAULT_APP_NAME
                        : configuredAppName;

        this.instanceId =
                "pid-" + ProcessHandle.current().pid();

        log.info("EventSender initialized. Endpoint: {}", telemetryEndpoint);
        log.info("EventSender appName: {}, instanceId: {}",
                appName, instanceId);
    }

    /**
     * Receives an event from EventCollector.
     */
    public void send(EventDTO eventDTO) {

        if (eventDTO == null) {
            return;
        }

        try {
            graphGateway.processEvent(eventDTO);
        } catch (RuntimeException e) {
            log.warn(
                    "Local graph processing failed for event {}: {}",
                    eventDTO.getEventType(),
                    e.getMessage()
            );
        }

        sendToDixy(eventDTO);
    }

    /**
     * Sends ordinary telemetry asynchronously.
     */
    private void sendToDixy(EventDTO event) {

        try {

            String json = toJson(event);

            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(URI.create(telemetryEndpoint))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Content-Type", "application/json");

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-API-Key", apiKey);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            ).thenAccept(response -> {

                int status = response.statusCode();

                if (status < 200 || status >= 300) {
                    log.warn(
                            "DIXY rejected telemetry. HTTP {} response={}",
                            status,
                            response.body()
                    );
                    return;
                }

                log.debug(
                        "DIXY telemetry accepted: {} thread={} id={}",
                        event.getEventType(),
                        event.getThreadName(),
                        event.getThreadId()
                );

            }).exceptionally(error -> {

                log.warn(
                        "Unable to send telemetry to DIXY: {}",
                        error.getMessage()
                );

                return null;
            });

        } catch (Exception e) {

            log.warn(
                    "Failed to construct telemetry request: {}",
                    e.getMessage()
            );
        }
    }

    /**
     * Sends the final monitoring shutdown event synchronously.
     *
     * This event does not contain threadId because it represents
     * the monitoring instance rather than an individual thread.
     */
    public void sendMonitorStop() {

        String json = "{"
                + "\"appName\":\"" + escape(appName) + "\","
                + "\"instanceId\":\"" + escape(instanceId) + "\","
                + "\"eventType\":\"MONITOR_STOP\""
                + "}";

        try {

            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(URI.create(telemetryEndpoint))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Content-Type", "application/json");

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-API-Key", apiKey);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.info(
                        "DIXY monitoring session stopped successfully: {}",
                        instanceId
                );
            } else {
                log.warn(
                        "MONITOR_STOP rejected. HTTP {} response={}",
                        status,
                        response.body()
                );
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.warn("MONITOR_STOP interrupted: {}", e.getMessage());

        } catch (Exception e) {

            log.warn(
                    "MONITOR_STOP delivery failed: {}",
                    e.getMessage()
            );
        }
    }

    /**
     * Creates JSON expected by TelemetryRequest.
     */
    private String toJson(EventDTO event) {

        StringBuilder json = new StringBuilder(512);

        json.append("{");

        appendString(json, "appName", appName);
        appendString(json, "instanceId", instanceId);

        appendString(
                json,
                "eventType",
                event.getEventType() == null
                        ? null
                        : event.getEventType().name()
        );

        appendLong(json, "threadId", event.getThreadId());
        appendString(json, "threadName", event.getThreadName());

        appendLong(json, "ownerThreadId", event.getOwnerThreadId());

        appendString(
                json,
                "ownerThreadName",
                event.getOwnerThreadName()
        );

        appendString(
                json,
                "lockId",
                String.valueOf(event.getLockAddress())
        );

        appendString(json, "lockType", "MONITOR");

        appendString(
                json,
                "carrierThread",
                event.getCarrierThreadName()
        );

        appendString(
                json,
                "timestamp",
                event.getTimestamp() == null
                        ? null
                        : event.getTimestamp().toString()
        );

        json.append("}");

        return json.toString();
    }

    private void appendString(
            StringBuilder json,
            String key,
            String value) {

        appendCommaIfNeeded(json);

        json.append("\"")
                .append(escape(key))
                .append("\":");

        if (value == null) {
            json.append("null");
        } else {
            json.append("\"")
                    .append(escape(value))
                    .append("\"");
        }
    }

    private void appendLong(
            StringBuilder json,
            String key,
            long value) {

        appendCommaIfNeeded(json);

        json.append("\"")
                .append(escape(key))
                .append("\":")
                .append(value);
    }

    private void appendCommaIfNeeded(StringBuilder json) {

        if (json.length() > 1) {
            json.append(",");
        }
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String removeTrailingSlash(String value) {

        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }
}
