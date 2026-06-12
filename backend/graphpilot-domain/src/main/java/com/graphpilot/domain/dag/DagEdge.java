package com.graphpilot.domain.dag;

import java.util.Objects;

public record DagEdge(TaskId fromTaskId, TaskId toTaskId) {

    public DagEdge {
        Objects.requireNonNull(fromTaskId, "fromTaskId must not be null");
        Objects.requireNonNull(toTaskId, "toTaskId must not be null");
        if (fromTaskId.equals(toTaskId)) {
            throw new IllegalArgumentException("DAG edge must not point to itself");
        }
    }
}
