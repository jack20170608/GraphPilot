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
 * End-to-end proof that the framework-free GraphPilot core runs under a Micronaut
 * host (ADR 0004): create + activate a workflow, trigger a run, and verify the
 * worker — wired via the Micronaut event glue — drives the DAG to SUCCEEDED with
 * persisted task output.
 *
 * <p>Uses the bootstrap-local deterministic {@code poc} task handler so execution
 * is stable and the output is verifiable.
 */
@MicronautTest
class WorkflowRunEndToEndTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @SuppressWarnings("unchecked")
    @Test
    void triggersRunAndExecutesDagToSucceeded() {
        // Create + activate a 2-task linear DAG using deterministic PoC tasks.
        var createRequest = Map.of(
                "name", "Micronaut ETL",
                "tasks", List.of(
                        Map.of("id", "extract", "name", "Extract", "type", "poc"),
                        Map.of("id", "load", "name", "Load", "type", "poc")),
                "edges", List.of(Map.of("fromTaskId", "extract", "toTaskId", "load")));

        var created = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/workflows", createRequest),
                Map.class).body();
        String workflowId = (String) created.get("id");
        assertThat(created.get("status")).isEqualTo("ACTIVE");

        // Trigger a run; the Micronaut event glue routes it to the worker core.
        var triggered = httpClient.toBlocking().exchange(
                HttpRequest.POST("/api/workflows/" + workflowId + "/runs", Map.of()),
                Map.class).body();
        String runId = (String) triggered.get("id");

        // Poll until the run reaches a terminal status.
        String status = awaitTerminalStatus(runId, Duration.ofSeconds(15));
        assertThat(status).isEqualTo("SUCCEEDED");

        // Verify task output was persisted by the worker core.
        List<Map<String, Object>> tasks = httpClient.toBlocking().retrieve(
                HttpRequest.GET("/api/workflow-runs/" + runId + "/tasks"),
                List.class);
        assertThat(tasks).hasSize(2);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.get("status")).isEqualTo("SUCCEEDED");
            assertThat(task.get("output")).asString().contains("ok");
        });
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
