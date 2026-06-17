package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class WorkflowRunApiTestSupport {

    private static final int WORKFLOW_TASK_COUNT = 3;
    private static final long TERMINAL_AWAIT_TIMEOUT_MS = 15_000L;
    private static final long TERMINAL_POLL_INTERVAL_MS = 100L;

    private WorkflowRunApiTestSupport() {
    }

    static ResponseEntity<Map<String, Object>> postWorkflow(
            TestRestTemplate restTemplate,
            String requestBody) {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.POST,
                jsonEntity(requestBody),
                new ParameterizedTypeReference<>() {});
    }

    static ResponseEntity<Map<String, Object>> activateWorkflow(
            TestRestTemplate restTemplate,
            String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/activate",
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    static ResponseEntity<Map<String, Object>> triggerWorkflowRun(
            TestRestTemplate restTemplate,
            String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/runs",
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    static ResponseEntity<Map<String, Object>> getWorkflowRun(
            TestRestTemplate restTemplate,
            String runId) {
        return restTemplate.exchange(
                "/api/workflow-runs/" + runId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    static ResponseEntity<List<Map<String, Object>>> listTaskRuns(
            TestRestTemplate restTemplate,
            String runId) {
        return restTemplate.exchange(
                "/api/workflow-runs/" + runId + "/tasks",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    static ResponseEntity<List<Map<String, Object>>> listWorkflowRuns(
            TestRestTemplate restTemplate,
            String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/runs",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    static String workflowRequestBody(String workflowName) {
        return """
                {
                  "name": "%s",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "transform", "name": "Transform data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "transform" },
                    { "fromTaskId": "transform", "toTaskId": "load" }
                  ]
                }
                """.formatted(workflowName);
    }

    static void assertWorkflowRun(Map<String, Object> workflowRun, String runId, String workflowId, String expectedStatus) {
        assertThat(workflowRun).containsEntry("id", runId);
        assertThat(workflowRun).containsEntry("workflowId", workflowId);
        assertThat(workflowRun).containsEntry("status", expectedStatus);
        assertThat(workflowRun).containsKey("triggeredAt");
    }

    static void assertTaskRuns(List<Map<String, Object>> taskRuns, String runId, String expectedStatus) {
        assertThat(taskRuns).hasSize(WORKFLOW_TASK_COUNT);
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("workflowRunId"))
                .containsOnly(runId);
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("status"))
                .containsOnly(expectedStatus);
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("taskId"))
                .containsExactly("extract", "transform", "load");
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("position"))
                .containsExactly(0, 1, 2);
    }

    /**
     * Polls the workflow run until it reaches a terminal status (SUCCEEDED or FAILED),
     * returning the final run body. The worker executes the run asynchronously, so the
     * status is non-deterministic immediately after the trigger.
     */
    static Map<String, Object> awaitWorkflowRunTerminal(TestRestTemplate restTemplate, String runId) {
        long deadline = System.currentTimeMillis() + TERMINAL_AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<Map<String, Object>> response = getWorkflowRun(restTemplate, runId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            String status = String.valueOf(body.get("status"));
            if (WorkflowRunTerminalStatuses.contains(status)) {
                return body;
            }
            sleepQuietly(TERMINAL_POLL_INTERVAL_MS);
        }
        throw new AssertionError("Workflow run " + runId + " did not reach a terminal status within "
                + TERMINAL_AWAIT_TIMEOUT_MS + "ms");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class WorkflowRunTerminalStatuses {
        private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED");

        static boolean contains(String status) {
            return TERMINAL.contains(status);
        }
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }
}
