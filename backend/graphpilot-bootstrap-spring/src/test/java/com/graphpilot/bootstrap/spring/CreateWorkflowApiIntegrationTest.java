package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
class CreateWorkflowApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createsWorkflowThroughHttpApi() {
        String requestBody = """
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
                """;

        ResponseEntity<Map<String, Object>> response = postWorkflow(requestBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("id")).asString().isNotBlank();
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .contains(response.getBody().get("id").toString());

        ResponseEntity<Map<String, Object>> getResponse = restTemplate.exchange(
                response.getHeaders().getLocation(),
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("status", "DRAFT");
    }

    @Test
    void returnsBadRequestForInvalidDag() {
        String requestBody = """
                {
                  "name": "Invalid workflow",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "load" },
                    { "fromTaskId": "load", "toTaskId": "extract" }
                  ]
                }
                """;

        ResponseEntity<Map<String, Object>> response = postWorkflow(requestBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("title", "Invalid workflow request");
    }

    private ResponseEntity<Map<String, Object>> postWorkflow(String requestBody) {
        return restTemplate.exchange(
                URI.create("/api/workflows"),
                HttpMethod.POST,
                jsonEntity(requestBody),
                new ParameterizedTypeReference<>() {});
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }
}
