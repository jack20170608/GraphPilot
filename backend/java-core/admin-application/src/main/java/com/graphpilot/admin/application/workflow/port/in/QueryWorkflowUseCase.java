package com.graphpilot.admin.application.workflow.port.in;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.List;

public interface QueryWorkflowUseCase {

    Workflow findById(WorkflowId workflowId);

    List<Workflow> findAll(int limit);
}