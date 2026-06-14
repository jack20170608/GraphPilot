package com.graphpilot.application.execution.port.in;

import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;

public interface TriggerWorkflowRunUseCase {

    WorkflowRunId trigger(WorkflowId workflowId);
}
