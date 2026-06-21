package com.graphpilot.adapter.worker.dto;

import java.util.Optional;

/**
 * Response DTO for worker task execution.
 * Returned from worker to scheduler after task execution.
 *
 * @param status task execution status (SUCCEEDED, FAILED, SKIPPED)
 * @param output task output string, present if SUCCEEDED
 * @param error error code, present if FAILED
 * @param errorMessage error message, present if FAILED
 */
public record TaskExecutionResponse(
        String status,
        String output,
        String error,
        String errorMessage) {

    public TaskExecutionResponse {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be null or blank");
        }
    }

    public boolean success() {
        return "SUCCEEDED".equalsIgnoreCase(status);
    }

    public boolean failed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    public boolean skipped() {
        return "SKIPPED".equalsIgnoreCase(status);
    }

    public Optional<String> outputValue() {
        return Optional.ofNullable(output);
    }

    public Optional<String> errorValue() {
        return Optional.ofNullable(error);
    }

    public Optional<String> errorMessageValue() {
        return Optional.ofNullable(errorMessage);
    }
}