package com.graphpilot.application.workflow.port.in;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;

public interface ChangeWorkflowLifecycleUseCase {

    Workflow activate(WorkflowId workflowId);

    Workflow pause(WorkflowId workflowId);

    Workflow resume(WorkflowId workflowId);

    Workflow archive(WorkflowId workflowId);
}
