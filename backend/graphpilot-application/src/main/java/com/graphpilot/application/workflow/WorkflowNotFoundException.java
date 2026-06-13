package com.graphpilot.application.workflow;

import com.graphpilot.domain.workflow.WorkflowId;

public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(WorkflowId workflowId) {
        super("Workflow not found: id=" + workflowId.value());
    }
}
