package com.graphpilot.domain.execution;

public record TimelineEventId(String value) {
    public TimelineEventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Timeline event id must not be blank");
        }
        value = value.trim();
    }

    public static TimelineEventId of(String value) {
        return new TimelineEventId(value);
    }
}
