package com.graphpilot.scheduler.application.execution;

import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.Objects;

public record ScanFailure(WorkflowRunId workflowRunId, String message) {
    public ScanFailure {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        message = (message == null || message.isBlank()) ? "Workflow run execution failed" : message.trim();
    }
}