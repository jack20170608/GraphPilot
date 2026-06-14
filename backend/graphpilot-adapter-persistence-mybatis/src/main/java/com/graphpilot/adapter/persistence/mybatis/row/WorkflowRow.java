package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRow(String id, String name, String status, Instant createdAt) {

    public WorkflowRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
