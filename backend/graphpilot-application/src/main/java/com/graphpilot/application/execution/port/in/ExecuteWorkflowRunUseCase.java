package com.graphpilot.application.execution.port.in;

import com.graphpilot.domain.execution.WorkflowRunId;

/**
 * Use case for executing a workflow run.
 * This is the entry point for the Worker to process a workflow.
 */
public interface ExecuteWorkflowRunUseCase {

    /**
     * Execute all pending tasks in a workflow run.
     * This method runs synchronously and handles task execution,
     * status updates, and retry logic.
     *
     * @param workflowRunId the workflow run to execute
     */
    void execute(WorkflowRunId workflowRunId);
}