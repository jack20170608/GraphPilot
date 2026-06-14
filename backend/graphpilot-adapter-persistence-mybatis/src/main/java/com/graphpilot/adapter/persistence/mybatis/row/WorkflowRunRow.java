package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRunRow(String id, String workflowId, String status, Instant triggeredAt) {

    public WorkflowRunRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }
}
