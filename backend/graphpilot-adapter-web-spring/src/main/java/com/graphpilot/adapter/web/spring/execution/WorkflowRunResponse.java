package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.domain.execution.WorkflowRun;
import java.time.Instant;

public record WorkflowRunResponse(
        String id,
        String workflowId,
        String status,
        Instant triggeredAt) {

    static WorkflowRunResponse from(WorkflowRun workflowRun) {
        return new WorkflowRunResponse(
                workflowRun.id().value(),
                workflowRun.workflowId().value(),
                workflowRun.status().name(),
                workflowRun.triggeredAt());
    }
}
