package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRunRepository;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
class PostgresWorkflowRunApiIntegrationTest {

    private static final int WORKFLOW_TASK_COUNT = 3;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private final TestRestTemplate restTemplate;
    private final WorkflowRunRepository workflowRunRepository;

    @Autowired
    PostgresWorkflowRunApiIntegrationTest(
            TestRestTemplate restTemplate,
            WorkflowRunRepository workflowRunRepository) {
        this.restTemplate = restTemplate;
        this.workflowRunRepository = workflowRunRepository;
    }

    @Test
    void usesMyBatisRepositoryAndPersistsPendingWorkflowRunThroughHttpApi() {
        assertThat(AopUtils.getTargetClass(workflowRunRepository)).isEqualTo(MyBatisWorkflowRunRepository.class);

        ResponseEntity<Map<String, Object>> createWorkflowResponse = postWorkflow(workflowRequestBody("Postgres Run ETL"));
        assertThat(createWorkflowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String workflowId = createWorkflowResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> activateResponse = activateWorkflow(workflowId);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody()).containsEntry("status", "ACTIVE");

        ResponseEntity<Map<String, Object>> triggerResponse = triggerWorkflowRun(workflowId);
        assertThat(triggerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(triggerResponse.getBody()).containsKey("id");
        String runId = triggerResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> runResponse = getWorkflowRun(runId);
        ResponseEntity<List<Map<String, Object>>> taskRunsResponse = listTaskRuns(runId);
        ResponseEntity<List<Map<String, Object>>> workflowRunsResponse = listWorkflowRuns(workflowId);

        assertThat(runResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertWorkflowRun(runResponse.getBody(), runId, workflowId);

        assertThat(taskRunsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertTaskRuns(taskRunsResponse.getBody(), runId);

        assertThat(workflowRunsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(workflowRunsResponse.getBody())
                .filteredOn(run -> run.get("id").equals(runId))
                .singleElement()
                .satisfies(run -> assertWorkflowRun(run, runId, workflowId));
    }

    private ResponseEntity<Map<String, Object>> postWorkflow(String requestBody) {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.POST,
                jsonEntity(requestBody),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> activateWorkflow(String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/activate",
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> triggerWorkflowRun(String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/runs",
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> getWorkflowRun(String runId) {
        return restTemplate.exchange(
                "/api/workflow-runs/" + runId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<List<Map<String, Object>>> listTaskRuns(String runId) {
        return restTemplate.exchange(
                "/api/workflow-runs/" + runId + "/tasks",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<List<Map<String, Object>>> listWorkflowRuns(String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/runs",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }

    private static String workflowRequestBody(String workflowName) {
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

    private static void assertWorkflowRun(Map<String, Object> workflowRun, String runId, String workflowId) {
        assertThat(workflowRun).containsEntry("id", runId);
        assertThat(workflowRun).containsEntry("workflowId", workflowId);
        assertThat(workflowRun).containsEntry("status", "PENDING");
        assertThat(workflowRun).containsKey("triggeredAt");
    }

    private static void assertTaskRuns(List<Map<String, Object>> taskRuns, String runId) {
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
}
