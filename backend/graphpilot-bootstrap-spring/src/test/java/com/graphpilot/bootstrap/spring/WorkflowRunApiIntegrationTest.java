package com.graphpilot.bootstrap.spring;

import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.activateWorkflow;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.assertTaskRuns;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.assertWorkflowRun;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.awaitWorkflowRunTerminal;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.listTaskRuns;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.listWorkflowRuns;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.postWorkflow;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.triggerWorkflowRun;
import static com.graphpilot.bootstrap.spring.WorkflowRunApiTestSupport.workflowRequestBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowRunApiIntegrationTest {

    private static final String EXPECTED_TERMINAL_STATUS = "SUCCEEDED";

    private final TestRestTemplate restTemplate;

    @Autowired
    WorkflowRunApiIntegrationTest(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

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

        ResponseEntity<Map<String, Object>> createWorkflowResponse = postWorkflow(restTemplate, requestBody);
        assertThat(createWorkflowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String workflowId = createWorkflowResponse.getBody().get("id").toString();
        assertThat(activateWorkflow(restTemplate, workflowId).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> triggerResponse = triggerWorkflowRun(restTemplate, workflowId);
        String runId = triggerResponse.getBody().get("id").toString();
        Map<String, Object> finalRun = awaitWorkflowRunTerminal(restTemplate, runId);
        assertWorkflowRun(finalRun, runId, workflowId, "SUCCEEDED");

        ResponseEntity<List<Map<String, Object>>> taskRunsResponse = listTaskRuns(restTemplate, runId);
        assertThat(taskRunsResponse.getBody())
                .filteredOn(task -> task.get("taskId").equals("load"))
                .singleElement()
                .satisfies(task -> assertThat(task.get("output")).isEqualTo("loaded abc"));
    }

    @Test
    void triggersAndExecutesWorkflowRunToSucceededThroughHttpApi() {
        ResponseEntity<Map<String, Object>> createWorkflowResponse = postWorkflow(restTemplate, workflowRequestBody("Memory Run ETL"));
        assertThat(createWorkflowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String workflowId = createWorkflowResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> activateResponse = activateWorkflow(restTemplate, workflowId);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody()).containsEntry("status", "ACTIVE");

        ResponseEntity<Map<String, Object>> triggerResponse = triggerWorkflowRun(restTemplate, workflowId);
        assertThat(triggerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(triggerResponse.getBody()).containsKey("id");
        String runId = triggerResponse.getBody().get("id").toString();

        // The worker executes the run asynchronously via the event listener; wait for it
        // to reach a terminal status before asserting the final state.
        Map<String, Object> finalRun = awaitWorkflowRunTerminal(restTemplate, runId);
        assertWorkflowRun(finalRun, runId, workflowId, EXPECTED_TERMINAL_STATUS);

        ResponseEntity<List<Map<String, Object>>> taskRunsResponse = listTaskRuns(restTemplate, runId);
        assertThat(taskRunsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertTaskRuns(taskRunsResponse.getBody(), runId, EXPECTED_TERMINAL_STATUS);

        ResponseEntity<List<Map<String, Object>>> workflowRunsResponse = listWorkflowRuns(restTemplate, workflowId);
        assertThat(workflowRunsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(workflowRunsResponse.getBody())
                .filteredOn(run -> run.get("id").equals(runId))
                .singleElement()
                .satisfies(run -> assertWorkflowRun(run, runId, workflowId, EXPECTED_TERMINAL_STATUS));
    }
}
