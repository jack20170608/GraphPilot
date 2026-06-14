package com.graphpilot.domain.execution;

public record TaskRunId(String value) {

    public TaskRunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task run id must not be blank");
        }
        value = value.trim();
    }

    public static TaskRunId of(String value) {
        return new TaskRunId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
