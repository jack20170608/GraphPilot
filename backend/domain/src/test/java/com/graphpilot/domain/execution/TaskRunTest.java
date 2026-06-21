package com.graphpilot.domain.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskRunTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-14T00:00:00Z");

    @Test
    void createsPendingTaskRunFromTaskSnapshot() {
        TaskDefinition task = new TaskDefinition(TaskId.of("extract"), "Extract data");

        TaskRun run = TaskRun.create(
                TaskRunId.of("task-run-1"),
                WorkflowRunId.of("workflow-run-1"),
                task,
                0,
                CREATED_AT);

        assertEquals(TaskRunId.of("task-run-1"), run.id());
        assertEquals(WorkflowRunId.of("workflow-run-1"), run.workflowRunId());
        assertEquals(TaskId.of("extract"), run.taskId());
        assertEquals("Extract data", run.taskName());
        assertEquals(TaskRunStatus.PENDING, run.status());
        assertEquals(0, run.position());
        assertEquals(CREATED_AT, run.createdAt());
    }

    @Test
    void rejectsNegativePosition() {
        TaskDefinition task = new TaskDefinition(TaskId.of("extract"), "Extract data");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TaskRun.create(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        task,
                        -1,
                        CREATED_AT));

        assertEquals("Task run position must not be negative", exception.getMessage());
    }

    @Test
    void taskRunIdTrimsAndRejectsBlank() {
        assertEquals("task-run-1", TaskRunId.of(" task-run-1 ").value());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TaskRunId.of(" "));

        assertEquals("Task run id must not be blank", exception.getMessage());
    }

    @Test
    void rejectsMissingRequiredFields() {
        TaskDefinition task = new TaskDefinition(TaskId.of("extract"), "Extract data");

        assertThrows(
                NullPointerException.class,
                () -> TaskRun.create(null, WorkflowRunId.of("workflow-run-1"), task, 0, CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.create(TaskRunId.of("task-run-1"), null, task, 0, CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.create(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        null,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.create(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        task,
                        0,
                        null));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        null,
                        WorkflowRunId.of("workflow-run-1"),
                        TaskId.of("extract"),
                        "Extract data",
                        TaskRunStatus.PENDING,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        null,
                        TaskId.of("extract"),
                        "Extract data",
                        TaskRunStatus.PENDING,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        null,
                        "Extract data",
                        TaskRunStatus.PENDING,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        TaskId.of("extract"),
                        null,
                        TaskRunStatus.PENDING,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        TaskId.of("extract"),
                        "Extract data",
                        null,
                        0,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        TaskId.of("extract"),
                        "Extract data",
                        TaskRunStatus.PENDING,
                        0,
                        null));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TaskRun.restore(
                        TaskRunId.of("task-run-1"),
                        WorkflowRunId.of("workflow-run-1"),
                        TaskId.of("extract"),
                        " ",
                        TaskRunStatus.PENDING,
                        0,
                        CREATED_AT));

        assertEquals("Task run task name must not be blank", exception.getMessage());
    }
}
