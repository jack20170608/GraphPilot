package com.graphpilot.domain.execution;

public record WorkflowRunId(String value) {

    public WorkflowRunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Workflow run id must not be blank");
        }
        value = value.trim();
    }

    public static WorkflowRunId of(String value) {
        return new WorkflowRunId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
