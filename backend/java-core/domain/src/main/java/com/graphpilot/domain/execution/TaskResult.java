package com.graphpilot.domain.execution;

import java.util.Optional;

/**
 * Represents the result of a task execution.
 */
public final class TaskResult {

    private final TaskRunStatus status;
    private final String output;
    private final String error;
    private final String errorMessage;

    private TaskResult(TaskRunStatus status, String output, String error, String errorMessage) {
        this.status = status;
        this.output = output;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static TaskResult success(String output) {
        return new TaskResult(TaskRunStatus.SUCCEEDED, output, null, null);
    }

    public static TaskResult failure(String error, String errorMessage) {
        return new TaskResult(TaskRunStatus.FAILED, null, error, errorMessage);
    }

    public static TaskResult skipped() {
        return new TaskResult(TaskRunStatus.SKIPPED, null, null, null);
    }

    public TaskRunStatus status() {
        return status;
    }

    public Optional<String> output() {
        return Optional.ofNullable(output);
    }

    public Optional<String> error() {
        return Optional.ofNullable(error);
    }

    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public boolean isSuccess() {
        return status == TaskRunStatus.SUCCEEDED;
    }

    public boolean isFailure() {
        return status == TaskRunStatus.FAILED;
    }
}