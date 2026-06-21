package com.graphpilot.domain.workflow;

import com.graphpilot.domain.dag.DagDefinition;
import java.time.Instant;
import java.util.Objects;

public record Workflow(
        WorkflowId id,
        WorkflowName name,
        DagDefinition dag,
        WorkflowStatus status,
        Instant createdAt) {

    public Workflow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dag, "dag must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Workflow create(
            WorkflowId id,
            WorkflowName name,
            DagDefinition dag,
            Instant createdAt) {
        return restore(id, name, dag, WorkflowStatus.DRAFT, createdAt);
    }

    public static Workflow restore(
            WorkflowId id,
            WorkflowName name,
            DagDefinition dag,
            WorkflowStatus status,
            Instant createdAt) {
        return new Workflow(id, name, dag, status, createdAt);
    }

    public Workflow activate() {
        if (status == WorkflowStatus.DRAFT) {
            return withStatus(WorkflowStatus.ACTIVE);
        }
        throw invalidTransition("activate");
    }

    public Workflow pause() {
        if (status == WorkflowStatus.ACTIVE) {
            return withStatus(WorkflowStatus.PAUSED);
        }
        throw invalidTransition("pause");
    }

    public Workflow resume() {
        if (status == WorkflowStatus.PAUSED) {
            return withStatus(WorkflowStatus.ACTIVE);
        }
        throw invalidTransition("resume");
    }

    public Workflow archive() {
        if (status == WorkflowStatus.ACTIVE || status == WorkflowStatus.PAUSED) {
            return withStatus(WorkflowStatus.ARCHIVED);
        }
        throw invalidTransition("archive");
    }

    private Workflow withStatus(WorkflowStatus nextStatus) {
        return restore(id, name, dag, nextStatus, createdAt);
    }

    private WorkflowLifecycleException invalidTransition(String action) {
        return new WorkflowLifecycleException("Cannot " + action + " workflow from status " + status);
    }
}
