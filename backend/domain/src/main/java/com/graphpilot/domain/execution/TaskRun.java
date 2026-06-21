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
        String taskType,
        TaskRunStatus status,
        int position,
        int retryCount,
        int maxRetries,
        String errorMessage,
        String output,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    public TaskRun(TaskRunId id, WorkflowRunId workflowRunId, TaskId taskId, String taskName,
            TaskRunStatus status, int position, Instant createdAt) {
        this(id, workflowRunId, taskId, taskName, "mock", status, position, 0, 3, null, null, null, null, createdAt);
    }

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
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        taskName = taskName.trim();
        taskType = (taskType == null || taskType.isBlank()) ? "mock" : taskType.trim();
    }

    public static TaskRun create(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskDefinition task,
            int position,
            Instant createdAt) {
        Objects.requireNonNull(task, "task must not be null");
        String type = task.type();
        return restore(
                id, workflowRunId, task.id(), task.name(), type,
                TaskRunStatus.PENDING, position,
                0, 3, null, null, null, null, createdAt);
    }

    public static TaskRun restore(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskId taskId,
            String taskName,
            String taskType,
            TaskRunStatus status,
            int position,
            int retryCount,
            int maxRetries,
            String errorMessage,
            String output,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt) {
        return new TaskRun(
                id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries,
                errorMessage, output, startedAt, finishedAt, createdAt);
    }

    /**
     * Backward-compatible restore method (without taskType, output, and retry fields).
     */
    public static TaskRun restore(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskId taskId,
            String taskName,
            String taskType,
            TaskRunStatus status,
            int position,
            int retryCount,
            int maxRetries,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries, errorMessage,
                null, startedAt, finishedAt, createdAt);
    }

    /**
     * Backward-compatible restore method (without taskType and retry fields).
     */
    public static TaskRun restore(
            TaskRunId id,
            WorkflowRunId workflowRunId,
            TaskId taskId,
            String taskName,
            TaskRunStatus status,
            int position,
            Instant createdAt) {
        return restore(id, workflowRunId, taskId, taskName, "mock",
                status, position, 0, 3, null, null, null, null, createdAt);
    }

    public boolean canRetry() {
        return status == TaskRunStatus.FAILED && retryCount < maxRetries;
    }

    public TaskRun withStatus(TaskRunStatus newStatus) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                newStatus, position, retryCount, maxRetries, errorMessage, output,
                startedAt, finishedAt, createdAt);
    }

    public TaskRun withStartedAt(Instant startedAt) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries, errorMessage, output,
                startedAt, finishedAt, createdAt);
    }

    public TaskRun withFinishedAt(Instant finishedAt) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries, errorMessage, output,
                startedAt, finishedAt, createdAt);
    }

    public TaskRun withErrorMessage(String error) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries, error, output,
                startedAt, finishedAt, createdAt);
    }

    public TaskRun withOutput(String output) {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                status, position, retryCount, maxRetries, errorMessage, output,
                startedAt, finishedAt, createdAt);
    }

    public TaskRun withIncrementedRetry() {
        return restore(id, workflowRunId, taskId, taskName, taskType,
                TaskRunStatus.PENDING, position, retryCount + 1, maxRetries,
                null, null, null, null, createdAt);
    }
}
