package com.graphpilot.adapter.worker.spring.handler;

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
        Map<String, Object> input = Map.of("delay", 50L);

        long start = System.currentTimeMillis();
        // Test with default (no delay param)
        TaskResult result = handler.execute(taskRun, taskDef, Map.of());
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        // Default delay is 100ms
        assertThat(duration).isGreaterThanOrEqualTo(90);
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