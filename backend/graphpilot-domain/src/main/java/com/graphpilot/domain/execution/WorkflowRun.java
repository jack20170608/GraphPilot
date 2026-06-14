package com.graphpilot.domain.execution;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.Objects;

public record WorkflowRun(
        WorkflowRunId id,
        WorkflowId workflowId,
        WorkflowRunStatus status,
        Instant triggeredAt) {

    public WorkflowRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }

    public static WorkflowRun create(
            WorkflowRunId id,
            Workflow workflow,
            Instant triggeredAt) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        if (workflow.status() != WorkflowStatus.ACTIVE) {
            throw new WorkflowRunTriggerException("Workflow run can only be triggered for ACTIVE workflow");
        }
        return restore(id, workflow.id(), WorkflowRunStatus.PENDING, triggeredAt);
    }

    public static WorkflowRun restore(
            WorkflowRunId id,
            WorkflowId workflowId,
            WorkflowRunStatus status,
            Instant triggeredAt) {
        return new WorkflowRun(id, workflowId, status, triggeredAt);
    }
}
