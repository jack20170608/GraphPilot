package com.graphpilot.application.workflow.port.in;

import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.domain.workflow.WorkflowId;

public interface CreateWorkflowUseCase {

    WorkflowId create(CreateWorkflowCommand command);
}
