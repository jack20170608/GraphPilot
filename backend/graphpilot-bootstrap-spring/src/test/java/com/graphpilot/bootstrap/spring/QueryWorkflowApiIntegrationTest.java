package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryWorkflowApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getsWorkflowCreatedThroughHttpApi() {
        ResponseEntity<Map<String, Object>> createResponse = postWorkflow("""
                {
                  "name": "Daily ETL",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "load" }
                  ]
                }
                """);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsKey("id");
        String workflowId = createResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> queryResponse = getWorkflow(workflowId);

        assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queryResponse.getBody()).containsEntry("id", workflowId);
        assertThat(queryResponse.getBody()).containsEntry("name", "Daily ETL");
        assertThat(queryResponse.getBody()).containsEntry("status", "DRAFT");
        assertThat(queryResponse.getBody()).containsKey("createdAt");
        assertThat(queryResponse.getBody().get("tasks")).asList().hasSize(2);
        assertThat(queryResponse.getBody().get("edges")).asList().hasSize(1);
    }

    @Test
    void returnsNotFoundWhenWorkflowDoesNotExist() {
        ResponseEntity<Map<String, Object>> response = getWorkflow("missing-workflow");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("title", "Workflow not found");
        assertThat(response.getBody()).containsEntry("detail", "Workflow was not found");
    }

    @Test
    void listsWorkflowsCreatedThroughHttpApi() {
        ResponseEntity<Map<String, Object>> firstCreateResponse = postWorkflow("""
                {
                  "name": "List API Workflow A",
                  "tasks": [
                    { "id": "extract-a", "name": "Extract A" }
                  ],
                  "edges": []
                }
                """);
        ResponseEntity<Map<String, Object>> secondCreateResponse = postWorkflow("""
                {
                  "name": "List API Workflow B",
                  "tasks": [
                    { "id": "extract-b", "name": "Extract B" }
                  ],
                  "edges": []
                }
                """);
        assertThat(firstCreateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondCreateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String firstWorkflowId = firstCreateResponse.getBody().get("id").toString();
        String secondWorkflowId = secondCreateResponse.getBody().get("id").toString();

        ResponseEntity<List<Map<String, Object>>> listResponse = listWorkflows();

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .extracting(workflow -> workflow.get("id"))
                .contains(firstWorkflowId, secondWorkflowId);
        assertThat(listResponse.getBody())
                .filteredOn(workflow -> workflow.get("id").equals(firstWorkflowId))
                .singleElement()
                .satisfies(workflow -> {
                    assertThat(workflow).containsEntry("name", "List API Workflow A");
                    assertThat(workflow).containsEntry("status", "DRAFT");
                    assertThat(workflow).containsKey("createdAt");
                    assertThat(workflow.get("tasks")).asList().hasSize(1);
                    assertThat(workflow.get("edges")).asList().isEmpty();
                });
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
}
