package com.graphpilot.domain.dag;

public record TaskId(String value) implements Comparable<TaskId> {

    public TaskId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task id must not be blank");
        }
        value = value.trim();
    }

    public static TaskId of(String value) {
        return new TaskId(value);
    }

    @Override
    public int compareTo(TaskId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
