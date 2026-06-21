package com.graphpilot.scheduler.application.execution.port.in;

import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;

/**
 * Use case for triggering a new workflow run.
 */
public interface TriggerWorkflowRunUseCase {

    /**
     * Trigger a new execution of a workflow.
     *
     * @param workflowId the workflow to run
     * @return the ID of the newly created workflow run
     */
    WorkflowRunId trigger(WorkflowId workflowId);
}