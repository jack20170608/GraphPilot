package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRunTimelineEventRow(
        String id,
        String workflowRunId,
        String taskRunId,
        String taskId,
        String type,
        String message,
        Instant occurredAt) {

    public WorkflowRunTimelineEventRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
