package com.graphpilot.scheduler.application.execution.service;

import com.graphpilot.scheduler.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.application.shared.port.ClockPort;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TimelineEventId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.List;
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

    public static TimelineRecorder noop(ClockPort clock) {
        return new TimelineRecorder(
                new WorkflowRunTimelineRepository() {
                    @Override
                    public WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event) {
                        return event;
                    }

                    @Override
                    public List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit) {
                        return List.of();
                    }
                },
                () -> TimelineEventId.of("noop"),
                () -> java.time.Instant.EPOCH);
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