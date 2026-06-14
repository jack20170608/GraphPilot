package com.graphpilot.adapter.worker.spring.handler;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP task handler for making HTTP requests.
 * Supports GET, POST, PUT, DELETE methods.
 */
public class HttpTaskHandler implements TaskHandler {

    private static final String TYPE = "http";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public HttpTaskHandler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        String url = getRequiredString(input, "url");
        String method = input.getOrDefault("method", "GET").toString().toUpperCase();
        String body = input.get("body") != null ? input.get("body").toString() : null;

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DEFAULT_TIMEOUT);

            // Set headers
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) input.get("headers");
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            // Set method and body
            HttpRequest request;
            switch (method) {
                case "GET" -> request = requestBuilder.GET().build();
                case "POST" -> {
                    requestBuilder.header("Content-Type", "application/json");
                    request = body != null
                            ? requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
                            : requestBuilder.POST(HttpRequest.BodyPublishers.noBody()).build();
                }
                case "PUT" -> {
                    requestBuilder.header("Content-Type", "application/json");
                    request = body != null
                            ? requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()
                            : requestBuilder.PUT(HttpRequest.BodyPublishers.noBody()).build();
                }
                case "DELETE" -> request = requestBuilder.DELETE().build();
                default -> {
                    return TaskResult.failure("UNKNOWN_METHOD", "Unsupported HTTP method: " + method);
                }
            }

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return TaskResult.success(response.body());
            } else {
                return TaskResult.failure(
                        "HTTP " + response.statusCode(),
                        response.body());
            }
        } catch (Exception e) {
            return TaskResult.failure(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String getRequiredString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required input: " + key);
        }
        return value.toString();
    }
}