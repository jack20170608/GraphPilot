package com.graphpilot.adapter.worker.http.dto;

import com.graphpilot.adapter.worker.dto.TaskExecutionResponse;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRunStatus;

/**
 * Mappers for converting between domain {@link TaskResult} and HTTP DTOs.
 */
public final class TaskExecutionMappers {

    private TaskExecutionMappers() {
        // utility
    }

    /**
     * Convert domain TaskResult to HTTP response DTO.
     */
    public static TaskExecutionResponse toResponse(TaskResult result) {
        String status = result.status().name();
        String output = result.output().orElse(null);
        String error = result.error().orElse(null);
        String errorMessage = result.errorMessage().orElse(null);
        return new TaskExecutionResponse(status, output, error, errorMessage);
    }

    /**
     * Convert HTTP response DTO to domain TaskResult.
     */
    public static TaskResult toTaskResult(TaskExecutionResponse response) {
        TaskRunStatus status;
        try {
            status = TaskRunStatus.valueOf(response.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskResult.failure("BAD_RESPONSE", "Unknown status: " + response.status());
        }

        String error = response.errorValue().orElse(null);
        String errorMessage = response.errorMessageValue().orElse(null);

        return switch (status) {
            case SUCCEEDED -> TaskResult.success(response.output());
            case FAILED -> TaskResult.failure(
                    error != null ? error : "UNKNOWN_ERROR",
                    errorMessage);
            case SKIPPED -> TaskResult.skipped();
            default -> TaskResult.failure("BAD_RESPONSE", "Unexpected status: " + status);
        };
    }
}