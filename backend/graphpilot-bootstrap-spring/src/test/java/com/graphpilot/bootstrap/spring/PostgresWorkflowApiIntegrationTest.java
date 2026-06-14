package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRepository;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.aop.support.AopUtils;
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
class PostgresWorkflowApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    void usesMyBatisRepositoryAndPersistsWorkflowThroughHttpApi() {
        assertThat(AopUtils.getTargetClass(workflowRepository)).isEqualTo(MyBatisWorkflowRepository.class);

        ResponseEntity<Map<String, Object>> createResponse = postWorkflow("""
                {
                  "name": "Postgres ETL",
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
                """);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsKey("id");
        String workflowId = createResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> getResponse = getWorkflow(workflowId);
        ResponseEntity<List<Map<String, Object>>> listResponse = listWorkflows();

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertWorkflow(getResponse.getBody(), workflowId);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .filteredOn(workflow -> workflow.get("id").equals(workflowId))
                .singleElement()
                .satisfies(PostgresWorkflowApiIntegrationTest::assertWorkflow);
    }

    private ResponseEntity<Map<String, Object>> postWorkflow(String requestBody) {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.POST,
                jsonEntity(requestBody),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> getWorkflow(String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<List<Map<String, Object>>> listWorkflows() {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }

    private static void assertWorkflow(Map<String, Object> workflow, String workflowId) {
        assertThat(workflow).containsEntry("id", workflowId);
        assertWorkflow(workflow);
    }

    private static void assertWorkflow(Map<String, Object> workflow) {
        assertThat(workflow).containsEntry("name", "Postgres ETL");
        assertThat(workflow).containsKey("createdAt");
        List<?> tasks = (List<?>) workflow.get("tasks");
        assertThat(tasks).hasSize(3);
        assertThat(tasks.stream()
                        .map(task -> ((Map<?, ?>) task).get("id").toString())
                        .toList())
                .containsExactly("extract", "transform", "load");

        List<?> edges = (List<?>) workflow.get("edges");
        assertThat(edges).hasSize(2);
        assertThat(edges.stream()
                        .map(edge -> ((Map<?, ?>) edge).get("fromTaskId") + "->" + ((Map<?, ?>) edge).get("toTaskId"))
                        .toList())
                .containsExactly("extract->transform", "transform->load");
    }
}
