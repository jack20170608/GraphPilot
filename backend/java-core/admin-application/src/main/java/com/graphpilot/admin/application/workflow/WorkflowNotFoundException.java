package com.graphpilot.admin.application.workflow;

/**
 * Exception thrown when a workflow is not found.
 * @deprecated Use {@link com.graphpilot.application.shared.exception.WorkflowNotFoundException} instead.
 */
@Deprecated
public class WorkflowNotFoundException extends com.graphpilot.application.shared.exception.WorkflowNotFoundException {

    public WorkflowNotFoundException(com.graphpilot.domain.workflow.WorkflowId workflowId) {
        super(workflowId);
    }
}