package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record TaskRunRow(
        String id,
        String workflowRunId,
        String taskId,
        String taskName,
        String taskType,
        String status,
        int position,
        int retryCount,
        int maxRetries,
        String errorMessage,
        String output,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    public TaskRunRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        taskType = (taskType == null || taskType.isBlank()) ? "mock" : taskType;
    }

    /**
     * Backward-compatible factory (without execution fields).
     */
    public static TaskRunRow of(
            String id, String workflowRunId, String taskId, String taskName,
            String status, int position, Instant createdAt) {
        return new TaskRunRow(
                id, workflowRunId, taskId, taskName, "mock",
                status, position, 0, 3, null, null, null, null, createdAt);
    }
}
