package com.graphpilot.adapter.worker.http;

import com.graphpilot.adapter.worker.dto.TaskExecutionRequest;
import com.graphpilot.adapter.worker.dto.TaskExecutionResponse;
import com.graphpilot.adapter.worker.http.dto.TaskExecutionMappers;
import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Remote task handler that dispatches task execution to a worker via HTTP.
 * Used by the scheduler when dispatch mode is "remote".
 */
public class HttpRemoteTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteTaskHandler.class);

    private final String workerBaseUrl;
    private final Duration timeout;
    private final RestClient restClient;

    public HttpRemoteTaskHandler(String workerBaseUrl, Duration timeout) {
        this.workerBaseUrl = workerBaseUrl;
        this.timeout = timeout;
        this.restClient = createRestClient();
    }

    private RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public String supportedType() {
        // This handler handles ALL task types - routing happens on worker side
        return "*";
    }

    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        var request = new TaskExecutionRequest(taskRun, task, input);
        String url = workerBaseUrl + "/api/worker/execute";

        log.info("Dispatching task to worker: url={}, taskType={}, taskId={}",
                url, taskRun.taskType(), taskRun.taskId());

        try {
            TaskExecutionResponse response = restClient.post()
                    .uri(url)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.warn("Worker returned 5xx: {}", resp.getStatusCode());
                    })
                    .body(TaskExecutionResponse.class);

            log.info("Received response from worker: status={}, taskId={}",
                    response.status(), taskRun.taskId());

            return TaskExecutionMappers.toTaskResult(response);

        } catch (RestClientResponseException e) {
            log.error("Worker server error: {} {}", e.getStatusCode(), e.getStatusText());
            return TaskResult.failure("REMOTE_UNAVAILABLE", e.getStatusCode() + " " + e.getStatusText());
        } catch (Exception e) {
            log.error("Failed to dispatch task to worker: {}", e.getMessage(), e);
            return TaskResult.failure("REMOTE_UNAVAILABLE", e.getMessage());
        }
    }
}