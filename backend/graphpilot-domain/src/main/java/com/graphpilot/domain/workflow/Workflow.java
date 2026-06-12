package com.graphpilot.domain.workflow;

import com.graphpilot.domain.dag.DagDefinition;
import java.time.Instant;
import java.util.Objects;

public record Workflow(
        WorkflowId id,
        WorkflowName name,
        DagDefinition dag,
        Instant createdAt) {

    public Workflow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dag, "dag must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Workflow create(
            WorkflowId id,
            WorkflowName name,
            DagDefinition dag,
            Instant createdAt) {
        return new Workflow(id, name, dag, createdAt);
    }
}
