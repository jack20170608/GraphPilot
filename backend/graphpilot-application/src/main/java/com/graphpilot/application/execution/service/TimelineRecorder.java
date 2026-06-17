package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.Objects;

public final class TimelineRecorder {

    private final WorkflowRunTimelineRepository repository;
    private final TimelineEventIdGeneratorPort idGenerator;
    private final ClockPort clock;

    public TimelineRecorder(
            WorkflowRunTimelineRepository repository,
            TimelineEventIdGeneratorPort idGenerator,
            ClockPort clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void run(WorkflowRunId runId, TimelineEventType type, String message) {
        repository.save(WorkflowRunTimelineEvent.runLevel(
                idGenerator.nextTimelineEventId(), runId, type, message, clock.now()));
    }

    public void task(WorkflowRunId runId, TaskRunId taskRunId, TaskId taskId,
            TimelineEventType type, String message) {
        repository.save(WorkflowRunTimelineEvent.taskLevel(
                idGenerator.nextTimelineEventId(), runId, taskRunId, taskId, type, message, clock.now()));
    }
}
