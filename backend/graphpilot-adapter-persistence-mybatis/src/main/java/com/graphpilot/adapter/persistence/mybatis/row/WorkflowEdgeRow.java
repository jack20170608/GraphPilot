package com.graphpilot.adapter.persistence.mybatis.row;

import java.util.Objects;

public record WorkflowEdgeRow(
        String workflowId,
        String sourceTaskId,
        String targetTaskId,
        int position) {

    public WorkflowEdgeRow {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(sourceTaskId, "sourceTaskId must not be null");
        Objects.requireNonNull(targetTaskId, "targetTaskId must not be null");
    }
}
