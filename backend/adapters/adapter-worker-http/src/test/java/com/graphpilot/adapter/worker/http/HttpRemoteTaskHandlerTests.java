package com.graphpilot.adapter.worker.http;

import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRemoteTaskHandlerTest {

    private MockWebServer mockWebServer;
    private HttpRemoteTaskHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        handler = new HttpRemoteTaskHandler(baseUrl, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void execute_success() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"status\":\"SUCCEEDED\",\"output\":\"test-output\",\"error\":null,\"errorMessage\":null}")
                .addHeader("Content-Type", "application/json"));

        TaskRun taskRun = createTaskRun("mock");
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> config = Map.of();

        TaskResult result = handler.execute(taskRun, taskDef, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("test-output");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/worker/execute");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    void execute_failure() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"status\":\"FAILED\",\"output\":null,\"error\":\"EXEC_ERROR\",\"errorMessage\":\"error msg\"}")
                .addHeader("Content-Type", "application/json"));

        TaskRun taskRun = createTaskRun("mock");
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> config = Map.of();

        TaskResult result = handler.execute(taskRun, taskDef, config);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("EXEC_ERROR");
        assertThat(result.errorMessage()).contains("error msg");
    }

    @Test
    void execute_serverError_returnsRemoteUnavailable() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("Internal Server Error")
                .setStatus("HTTP/1.1 500 Internal Server Error"));

        TaskRun taskRun = createTaskRun("mock");
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> config = Map.of();

        TaskResult result = handler.execute(taskRun, taskDef, config);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("REMOTE_UNAVAILABLE");
    }

    @Test
    void execute_connectionFailed_returnsRemoteUnavailable() throws Exception {
        // Point to non-running port to simulate connection failure
        handler = new HttpRemoteTaskHandler("http://localhost:65535", Duration.ofMillis(500));

        TaskRun taskRun = createTaskRun("mock");
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> config = Map.of();

        TaskResult result = handler.execute(taskRun, taskDef, config);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("REMOTE_UNAVAILABLE");
    }

    @Test
    void execute_skipped() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"status\":\"SKIPPED\",\"output\":null,\"error\":null,\"errorMessage\":null}")
                .addHeader("Content-Type", "application/json"));

        TaskRun taskRun = createTaskRun("mock");
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> config = Map.of();

        TaskResult result = handler.execute(taskRun, taskDef, config);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailure()).isFalse();
    }

    private TaskRun createTaskRun(String taskType) {
        return TaskRun.restore(
                TaskRunId.of("test-task-run-id"),
                WorkflowRunId.of("test-workflow-run-id"),
                TaskId.of("test-task-id"),
                "test-task",
                taskType,
                TaskRunStatus.PENDING,
                0,
                0,
                3,
                null,
                null,
                null,
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    private TaskDefinition createTaskDefinition() {
        return new TaskDefinition(TaskId.of("test-task-id"), "test-task", "mock", TaskConfig.empty());
    }
}