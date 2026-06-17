package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.time.Instant;

public record TimelineEventResponse(
        String id,
        String workflowRunId,
        String taskRunId,
        String taskId,
        String type,
        String message,
        Instant occurredAt) {

    static TimelineEventResponse from(WorkflowRunTimelineEvent event) {
        return new TimelineEventResponse(
                event.id().value(),
                event.workflowRunId().value(),
                event.taskRunId() == null ? null : event.taskRunId().value(),
                event.taskId() == null ? null : event.taskId().value(),
                event.type().name(),
                event.message(),
                event.occurredAt());
    }
}
