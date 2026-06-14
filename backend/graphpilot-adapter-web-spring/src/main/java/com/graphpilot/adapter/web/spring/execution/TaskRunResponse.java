package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.domain.execution.TaskRun;
import java.time.Instant;

public record TaskRunResponse(
        String id,
        String workflowRunId,
        String taskId,
        String taskName,
        String status,
        int position,
        Instant createdAt) {

    static TaskRunResponse from(TaskRun taskRun) {
        return new TaskRunResponse(
                taskRun.id().value(),
                taskRun.workflowRunId().value(),
                taskRun.taskId().value(),
                taskRun.taskName(),
                taskRun.status().name(),
                taskRun.position(),
                taskRun.createdAt());
    }
}
