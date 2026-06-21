package com.graphpilot.admin.application.workflow.service;

import com.graphpilot.admin.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.admin.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.shared.port.ClockPort;
import com.graphpilot.application.shared.port.IdGeneratorPort;
import com.graphpilot.application.shared.port.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.util.Objects;

public final class CreateWorkflowService implements CreateWorkflowUseCase {

    private final WorkflowRepository workflowRepository;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;

    public CreateWorkflowService(
            WorkflowRepository workflowRepository,
            IdGeneratorPort idGenerator,
            ClockPort clock) {
        this.workflowRepository = Objects.requireNonNull(
                workflowRepository,
                "workflowRepository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public WorkflowId create(CreateWorkflowCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        DagDefinition dag = new DagDefinition(command.tasks(), command.edges());
        Workflow workflow = Workflow.create(
                idGenerator.nextWorkflowId(),
                WorkflowName.of(command.name()),
                dag,
                clock.now());

        return workflowRepository.save(workflow).id();
    }
}