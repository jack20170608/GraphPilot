package com.graphpilot.adapter.worker.spring.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpTaskHandlerTest {

    private final HttpTaskHandler handler = new HttpTaskHandler();

    @Test
    void supportedTypeReturnsHttp() {
        assertThat(handler.supportedType()).isEqualTo("http");
    }

    @Test
    void executeThrowsWhenUrlMissing() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();

        assertThatThrownBy(() -> handler.execute(taskRun, taskDef, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required input: url");
    }

    @Test
    void executeReturnsFailureForUnknownMethod() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of(
                "url", "http://example.com",
                "method", "INVALID");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.errorMessage()).isPresent();
        assertThat(result.errorMessage().get()).contains("Unsupported HTTP method");
    }

    @Test
    void executeReturnsFailureForConnectionError() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        // Use invalid URL to trigger connection error
        Map<String, Object> input = Map.of(
                "url", "http://localhost:1/no-such-endpoint",
                "method", "GET");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isPresent();
    }

    @Test
    void executeSupportsGetMethod() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of(
                "url", "http://httpbin.org/get",
                "method", "GET");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        // httpbin.org/get should return 200
        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(result.output()).isPresent();
    }

    @Test
    void executeSupportsPostMethod() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of(
                "url", "http://httpbin.org/post",
                "method", "POST",
                "body", "{\"test\": true}");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    private TaskRun createTaskRun() {
        return TaskRun.restore(
                TaskRunId.of("task-run-1"),
                WorkflowRunId.of("run-1"),
                TaskId.of("task-1"),
                "HTTP Task",
                TaskRunStatus.PENDING,
                0,
                Instant.now());
    }

    private TaskDefinition createTaskDefinition() {
        return new TaskDefinition(
                TaskId.of("task-1"),
                "HTTP Task",
                "http");
    }
}