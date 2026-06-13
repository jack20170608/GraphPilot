package com.graphpilot.application.workflow.port.in;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;

public interface QueryWorkflowUseCase {

    Workflow findById(WorkflowId workflowId);
}
