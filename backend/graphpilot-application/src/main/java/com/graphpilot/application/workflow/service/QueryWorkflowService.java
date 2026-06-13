package com.graphpilot.application.workflow.service;

import com.graphpilot.application.workflow.WorkflowNotFoundException;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;

public final class QueryWorkflowService implements QueryWorkflowUseCase {

    private final WorkflowRepository workflowRepository;

    public QueryWorkflowService(WorkflowRepository workflowRepository) {
        this.workflowRepository = Objects.requireNonNull(
                workflowRepository,
                "workflowRepository must not be null");
    }

    @Override
    public Workflow findById(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }
}
