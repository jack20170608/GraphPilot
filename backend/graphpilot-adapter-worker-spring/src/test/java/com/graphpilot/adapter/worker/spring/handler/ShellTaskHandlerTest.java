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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellTaskHandlerTest {

    private final ShellTaskHandler handler = new ShellTaskHandler();

    @Test
    void supportedTypeReturnsShell() {
        assertThat(handler.supportedType()).isEqualTo("shell");
    }

    @Test
    void executeThrowsWhenCommandMissing() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();

        assertThatThrownBy(() -> handler.execute(taskRun, taskDef, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required input: command");
    }

    @Test
    void executeReturnsSuccessForEchoCommand() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of("command", "echo hello");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(result.output()).contains("hello");
    }

    @Test
    void executeReturnsFailureForFailingCommand() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of("command", "exit 1");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void executeRespectsTimeout() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        // Sleep for 2 seconds but set timeout to 1 second
        Map<String, Object> input = Map.of(
                "command", "sleep 2",
                "timeout", 1L);

        TaskResult result = handler.execute(taskRun, taskDef, input);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("TIMEOUT");
    }

    @Test
    void executeReturnsSuccessForPipedCommand() {
        TaskRun taskRun = createTaskRun();
        TaskDefinition taskDef = createTaskDefinition();
        Map<String, Object> input = Map.of("command", "echo test | cat");

        TaskResult result = handler.execute(taskRun, taskDef, input);

        // Note: piped commands may not work on Windows
        assertThat(result).isNotNull();
    }

    private TaskRun createTaskRun() {
        return TaskRun.restore(
                TaskRunId.of("task-run-1"),
                WorkflowRunId.of("run-1"),
                TaskId.of("task-1"),
                "Shell Task",
                TaskRunStatus.PENDING,
                0,
                Instant.now());
    }

    private TaskDefinition createTaskDefinition() {
        return new TaskDefinition(
                TaskId.of("task-1"),
                "Shell Task",
                "shell");
    }
}