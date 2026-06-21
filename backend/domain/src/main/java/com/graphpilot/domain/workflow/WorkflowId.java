package com.graphpilot.domain.workflow;

public record WorkflowId(String value) {

    public WorkflowId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Workflow id must not be blank");
        }
        value = value.trim();
    }

    public static WorkflowId of(String value) {
        return new WorkflowId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
