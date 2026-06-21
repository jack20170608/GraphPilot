package com.graphpilot.domain.execution;

import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import java.util.Objects;

public record WorkflowRunTimelineEvent(
        TimelineEventId id,
        WorkflowRunId workflowRunId,
        TaskRunId taskRunId,
        TaskId taskId,
        TimelineEventType type,
        String message,
        Instant occurredAt) {

    public WorkflowRunTimelineEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Timeline event message must not be blank");
        }
        message = message.trim();
    }

    public static WorkflowRunTimelineEvent runLevel(
            TimelineEventId id,
            WorkflowRunId workflowRunId,
            TimelineEventType type,
            String message,
            Instant occurredAt) {
        return new WorkflowRunTimelineEvent(id, workflowRunId, null, null, type, message, occurredAt);
    }

    public static WorkflowRunTimelineEvent taskLevel(
            TimelineEventId id,
            WorkflowRunId workflowRunId,
            TaskRunId taskRunId,
            TaskId taskId,
            TimelineEventType type,
            String message,
            Instant occurredAt) {
        Objects.requireNonNull(taskRunId, "taskRunId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        return new WorkflowRunTimelineEvent(id, workflowRunId, taskRunId, taskId, type, message, occurredAt);
    }
}
