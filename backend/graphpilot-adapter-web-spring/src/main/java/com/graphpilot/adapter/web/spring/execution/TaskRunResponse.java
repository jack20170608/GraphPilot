package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.domain.execution.TaskRun;
import java.time.Instant;

public record TaskRunResponse(
        String id,
        String workflowRunId,
        String taskId,
        String taskName,
        String taskType,
        String status,
        int position,
        int retryCount,
        int maxRetries,
        String errorMessage,
        String output,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    static TaskRunResponse from(TaskRun taskRun) {
        return new TaskRunResponse(
                taskRun.id().value(),
                taskRun.workflowRunId().value(),
                taskRun.taskId().value(),
                taskRun.taskName(),
                taskRun.taskType(),
                taskRun.status().name(),
                taskRun.position(),
                taskRun.retryCount(),
                taskRun.maxRetries(),
                taskRun.errorMessage(),
                taskRun.output(),
                taskRun.startedAt(),
                taskRun.finishedAt(),
                taskRun.createdAt());
    }
}
