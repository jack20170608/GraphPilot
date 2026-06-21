package com.graphpilot.scheduler.application.execution;

public final class TaskConfigExpressionException extends RuntimeException {
    public TaskConfigExpressionException(String message) {
        super(message);
    }

    public TaskConfigExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}