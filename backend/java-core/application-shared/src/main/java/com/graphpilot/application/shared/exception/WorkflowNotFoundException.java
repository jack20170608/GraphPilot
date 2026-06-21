package com.graphpilot.application.shared.exception;

import com.graphpilot.domain.workflow.WorkflowId;

/**
 * Exception thrown when a workflow is not found.
 * This exception is shared between admin and scheduler modules.
 */
public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(WorkflowId workflowId) {
        super("Workflow not found: id=" + workflowId.value());
    }
}