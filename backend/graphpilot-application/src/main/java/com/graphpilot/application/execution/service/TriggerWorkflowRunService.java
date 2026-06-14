package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.EventPublisherPort;
import com.graphpilot.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.WorkflowNotFoundException;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TriggerWorkflowRunService implements TriggerWorkflowRunUseCase {

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkflowRunIdGeneratorPort workflowRunIdGenerator;
    private final TaskRunIdGeneratorPort taskRunIdGenerator;
    private final ClockPort clock;
    private final EventPublisherPort eventPublisher;

    public TriggerWorkflowRunService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunIdGeneratorPort workflowRunIdGenerator,
            TaskRunIdGeneratorPort taskRunIdGenerator,
            ClockPort clock,
            EventPublisherPort eventPublisher) {
        this.workflowRepository = Objects.requireNonNull(
                workflowRepository,
                "workflowRepository must not be null");
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository,
                "workflowRunRepository must not be null");
        this.workflowRunIdGenerator = Objects.requireNonNull(
                workflowRunIdGenerator,
                "workflowRunIdGenerator must not be null");
        this.taskRunIdGenerator = Objects.requireNonNull(
                taskRunIdGenerator,
                "taskRunIdGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    public WorkflowRunId trigger(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Workflow workflow = workflowRepository.findByIdForRunTrigger(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        Instant triggeredAt = clock.now();
        WorkflowRun workflowRun = WorkflowRun.create(
                workflowRunIdGenerator.nextWorkflowRunId(),
                workflow,
                triggeredAt);
        List<TaskRun> taskRuns = createTaskRuns(workflowRun, workflow.dag().tasks(), triggeredAt);

        WorkflowRun savedRun = workflowRunRepository.save(workflowRun, taskRuns);

        // Publish event to trigger worker execution
        eventPublisher.publish(new WorkflowRunCreatedEvent(savedRun.id(), workflow.id()));

        return savedRun.id();
    }

    private List<TaskRun> createTaskRuns(
            WorkflowRun workflowRun,
            List<TaskDefinition> tasks,
            Instant createdAt) {
        List<TaskRun> taskRuns = new ArrayList<>();
        for (int position = 0; position < tasks.size(); position++) {
            taskRuns.add(TaskRun.create(
                    taskRunIdGenerator.nextTaskRunId(),
                    workflowRun.id(),
                    tasks.get(position),
                    position,
                    createdAt));
        }
        return List.copyOf(taskRuns);
    }
}
