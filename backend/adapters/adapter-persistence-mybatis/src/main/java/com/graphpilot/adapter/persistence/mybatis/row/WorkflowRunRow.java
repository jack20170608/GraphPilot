package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRunRow(
        String id,
        String workflowId,
        String status,
        Instant triggeredAt,
        Instant startedAt,
        Instant finishedAt) {

    public WorkflowRunRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }

    /**
     * Backward-compatible constructor (without execution timestamps).
     */
    public static WorkflowRunRow of(String id, String workflowId, String status, Instant triggeredAt) {
        return new WorkflowRunRow(id, workflowId, status, triggeredAt, null, null);
    }
}