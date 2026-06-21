package com.graphpilot.adapter.worker.handler;

import static org.assertj.core.api.Assertions.assertThat;

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

class MockTaskHandlerTest {

    private final MockTaskHandler handler = new MockTaskHandler();

    @Test
    void supportedTypeReturnsMock() {
        assertThat(handler.supportedType()).isEqualTo("mock");
    }

    @Test
    void executeReturnsSuccessWithDefaultInput() {
        TaskRun taskRun = createTaskRun(TaskRunStatus.PENDING);
        TaskDefinition taskDef = createTaskDefinition();

        TaskResult result = handler.execute(taskRun, taskDef, Map.of());

        // Mock handler returns success/failure based on random, test that it returns a result
        assertThat(result).isNotNull();
        assertThat(result.status()).isIn(TaskRunStatus.SUCCEEDED, TaskRunStatus.FAILED);
    }

    @Test
    void executeRespectsInputDelay() {
        TaskRun taskRun = createTaskRun(TaskRunStatus.PENDING);
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of("delayMs", 50L, "success", true);

        long start = System.currentTimeMillis();
        TaskResult result = handler.execute(taskRun, taskDef, input);
        long duration = System.currentTimeMillis() - start;

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(duration).isGreaterThanOrEqualTo(45);
    }

    @Test
    void executeCanBeForcedToSucceedByConfig() {
        TaskResult result = handler.execute(
                createTaskRun(TaskRunStatus.PENDING),
                createTaskDefinition(),
                Map.of("success", true, "delayMs", 0));

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    @Test
    void executeCanBeForcedToFailByConfig() {
        TaskResult result = handler.execute(
                createTaskRun(TaskRunStatus.PENDING),
                createTaskDefinition(),
                Map.of("success", false, "delayMs", 0));

        assertThat(result.status()).isEqualTo(TaskRunStatus.FAILED);
    }

    @Test
    void executeUsesConfiguredOutputWhenForcedToSucceed() {
        TaskResult result = handler.execute(
                createTaskRun(TaskRunStatus.PENDING),
                createTaskDefinition(),
                Map.of("success", true, "delayMs", 0, "output", "{\"id\":\"abc\"}"));

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(result.output()).contains("{\"id\":\"abc\"}");
    }

    @Test
    void executeUsesDefaultOutputWhenForcedToSucceedWithoutOutput() {
        TaskResult result = handler.execute(
                createTaskRun(TaskRunStatus.PENDING),
                createTaskDefinition(),
                Map.of("success", true, "delayMs", 0));

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(result.output()).contains("Mock task completed successfully");
    }

    private TaskRun createTaskRun(TaskRunStatus status) {
        return TaskRun.restore(
                TaskRunId.of("task-run-1"),
                WorkflowRunId.of("run-1"),
                TaskId.of("task-1"),
                "Test Task",
                status,
                0,
                Instant.now());
    }

    private TaskDefinition createTaskDefinition() {
        return new TaskDefinition(
                TaskId.of("task-1"),
                "Test Task",
                "mock");
    }
}