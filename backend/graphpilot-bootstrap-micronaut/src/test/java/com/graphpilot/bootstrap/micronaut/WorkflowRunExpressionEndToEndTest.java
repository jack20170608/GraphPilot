package com.graphpilot.bootstrap.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof that task config expressions resolve correctly under Micronaut,
 * mirroring the Spring expression E2E test.
 *
 * <p>An "extract" mock task outputs JSON, and a downstream "load" mock task uses an
 * embedded expression to reference a field from that output.
 */
@MicronautTest
class WorkflowRunExpressionEndToEndTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @SuppressWarnings("unchecked")
    @Test
    void resolvesTaskConfigExpressionFromUpstreamOutput() {
        Map<String, Object> extractConfig = Map.of(
                "success", true,
                "delayMs", 0,
                "output", "{\"id\":\"abc\"}");
        Map<String, Object> loadConfig = Map.of(
                "success", true,
                "delayMs", 0,
                "output", "loaded ${tasks.extract.output.id}");
        Map<String, Object> requestBody = Map.of(
                "name", "Expression ETL",
                "tasks", List.of(
                        Map.of("id", "extract", "name", "Extract", "type", "mock", "config", extractConfig),
                        Map.of("id", "load", "name", "Load", "type", "mock", "config", loadConfig)),
                "edges", List.of(
                        Map.of("fromTaskId", "extract", "toTaskId", "load")));

        var created = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/workflows", requestBody),
                Map.class).body();
        String workflowId = (String) created.get("id");

        httpClient.toBlocking().retrieve(
                HttpRequest.POST("/api/workflows/" + workflowId + "/activate", Map.of()), Map.class);

        var triggered = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/workflows/" + workflowId + "/runs", Map.of()),
                Map.class).body();
        String runId = (String) triggered.get("id");

        String status = awaitTerminalStatus(runId, Duration.ofSeconds(15));
        assertThat(status).isEqualTo("SUCCEEDED");

        List<Map<String, Object>> tasks = httpClient.toBlocking().retrieve(
                HttpRequest.GET("/api/workflow-runs/" + runId + "/tasks"),
                List.class);
        assertThat(tasks)
                .filteredOn(task -> task.get("taskId").equals("load"))
                .singleElement()
                .satisfies(task -> assertThat(task.get("output")).isEqualTo("loaded abc"));
    }

    @SuppressWarnings("unchecked")
    private String awaitTerminalStatus(String runId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> run = httpClient.toBlocking().retrieve(
                    HttpRequest.GET("/api/workflow-runs/" + runId), Map.class);
            String status = String.valueOf(run.get("status"));
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            sleep(Duration.ofMillis(200));
        }
        throw new AssertionError("Run " + runId + " did not reach terminal status within " + timeout);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
