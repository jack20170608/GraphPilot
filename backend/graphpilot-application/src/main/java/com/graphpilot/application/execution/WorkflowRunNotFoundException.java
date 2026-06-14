package com.graphpilot.application.execution;

import com.graphpilot.domain.execution.WorkflowRunId;

public class WorkflowRunNotFoundException extends RuntimeException {

    public WorkflowRunNotFoundException(WorkflowRunId workflowRunId) {
        super("Workflow run not found: id=" + workflowRunId.value());
    }
}
