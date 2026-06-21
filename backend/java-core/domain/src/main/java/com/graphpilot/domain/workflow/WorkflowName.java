package com.graphpilot.domain.workflow;

public record WorkflowName(String value) {

    public WorkflowName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Workflow name must not be blank");
        }
        value = value.trim();
    }

    public static WorkflowName of(String value) {
        return new WorkflowName(value);
    }
}
