package com.graphpilot.admin.application.workflow.service;

import com.graphpilot.admin.application.workflow.WorkflowNotFoundException;
import com.graphpilot.admin.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.shared.port.WorkflowRepository;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class ChangeWorkflowLifecycleService implements ChangeWorkflowLifecycleUseCase {

    private final WorkflowRepository workflowRepository;

    public ChangeWorkflowLifecycleService(WorkflowRepository workflowRepository) {
        this.workflowRepository = Objects.requireNonNull(
                workflowRepository,
                "workflowRepository must not be null");
    }

    @Override
    public Workflow activate(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::activate);
    }

    @Override
    public Workflow pause(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::pause);
    }

    @Override
    public Workflow resume(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::resume);
    }

    @Override
    public Workflow archive(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::archive);
    }

    private Workflow changeStatus(
            WorkflowId workflowId,
            UnaryOperator<Workflow> transition) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        Workflow transitionedWorkflow = transition.apply(workflow);

        return workflowRepository.save(transitionedWorkflow);
    }
}