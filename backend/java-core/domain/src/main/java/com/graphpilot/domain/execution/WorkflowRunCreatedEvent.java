package com.graphpilot.domain.execution;

import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;

/**
 * Domain event published when a new WorkflowRun is created.
 * Used by Worker to trigger execution.
 */
public final class WorkflowRunCreatedEvent {

    private final WorkflowRunId workflowRunId;
    private final WorkflowId workflowId;

    public WorkflowRunCreatedEvent(WorkflowRunId workflowRunId, WorkflowId workflowId) {
        this.workflowRunId = Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId must not be null");
    }

    public WorkflowRunId workflowRunId() {
        return workflowRunId;
    }

    public WorkflowId workflowId() {
        return workflowId;
    }
}