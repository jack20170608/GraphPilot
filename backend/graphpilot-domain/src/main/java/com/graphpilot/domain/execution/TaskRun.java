package com.graphpilot.domain.execution;

import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskRun(
        TaskRunId id,
        WorkflowRunId workflowRunId,
        TaskId taskId,
        String taskName,
        TaskRunStatus status,
        int position,
        Instant createdAt) {

    public TaskRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (taskName.isBlank()) {
            throw new IllegalArgumentException("Task run task name must not be blank");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Task run position must not be negative");
        }
        taskName = taskName.trim();
    }

    public static TaskRun create(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskDefinition task,
            int position,
            Instant createdAt) {
        Objects.requireNonNull(task, "task must not be null");
        return restore(id, workflowRunId, task.id(), task.name(), TaskRunStatus.PENDING, position, createdAt);
    }

    public static TaskRun restore(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskId taskId,
            String taskName,
            TaskRunStatus status,
            int position,
            Instant createdAt) {
        return new TaskRun(id, workflowRunId, taskId, taskName, status, position, createdAt);
    }
}
