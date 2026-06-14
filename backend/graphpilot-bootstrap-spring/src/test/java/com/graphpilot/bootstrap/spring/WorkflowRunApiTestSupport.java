package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class WorkflowRunApiTestSupport {

    private static final int WORKFLOW_TASK_COUNT = 3;

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

    static void assertWorkflowRun(Map<String, Object> workflowRun, String runId, String workflowId) {
        assertThat(workflowRun).containsEntry("id", runId);
        assertThat(workflowRun).containsEntry("workflowId", workflowId);
        assertThat(workflowRun).containsEntry("status", "PENDING");
        assertThat(workflowRun).containsKey("triggeredAt");
    }

    static void assertTaskRuns(List<Map<String, Object>> taskRuns, String runId) {
        assertThat(taskRuns).hasSize(WORKFLOW_TASK_COUNT);
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("workflowRunId"))
                .containsOnly(runId);
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("status"))
                .containsOnly("PENDING");
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("taskId"))
                .containsExactly("extract", "transform", "load");
        assertThat(taskRuns)
                .extracting(taskRun -> taskRun.get("position"))
                .containsExactly(0, 1, 2);
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }
}
