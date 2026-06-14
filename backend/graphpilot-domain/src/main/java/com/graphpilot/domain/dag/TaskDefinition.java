package com.graphpilot.domain.dag;

import java.util.Objects;

public record TaskDefinition(TaskId id, String name, String type) {

    public static final String DEFAULT_TYPE = "mock";

    public TaskDefinition(TaskId id, String name) {
        this(id, name, DEFAULT_TYPE);
    }

    public TaskDefinition {
        Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }
        name = name.trim();
        type = (type == null || type.isBlank()) ? DEFAULT_TYPE : type.trim();
    }

    public static TaskDefinition of(TaskId id, String name) {
        return new TaskDefinition(id, name, DEFAULT_TYPE);
    }

    public static TaskDefinition of(TaskId id, String name, String type) {
        return new TaskDefinition(id, name, type);
    }
}
