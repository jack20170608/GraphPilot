package com.graphpilot.domain.dag;

import java.util.Objects;

public record TaskDefinition(TaskId id, String name) {

    public TaskDefinition {
        Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }
        name = name.trim();
    }
}
