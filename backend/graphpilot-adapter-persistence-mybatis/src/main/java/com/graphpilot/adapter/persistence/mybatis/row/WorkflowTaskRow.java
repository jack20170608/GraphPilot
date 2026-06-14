package com.graphpilot.adapter.persistence.mybatis.row;

import java.util.Objects;

public record WorkflowTaskRow(
        String workflowId,
        String taskId,
        String name,
        int position) {

    public WorkflowTaskRow {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
