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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
class PostgresWorkflowRunApiIntegrationTest {

    private static final String EXPECTED_TERMINAL_STATUS = "SUCCEEDED";

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
    void usesMyBatisRepositoryAndExecutesWorkflowRunToSucceededThroughHttpApi() {
        assertThat(AopUtils.getTargetClass(workflowRunRepository)).isEqualTo(MyBatisWorkflowRunRepository.class);

        ResponseEntity<Map<String, Object>> createWorkflowResponse = postWorkflow(restTemplate, workflowRequestBody("Postgres Run ETL"));
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
