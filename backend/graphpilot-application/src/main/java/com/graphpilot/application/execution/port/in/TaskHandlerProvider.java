package com.graphpilot.application.execution.port.in;

import java.util.List;

/**
 * Provides task handlers based on task type.
 */
public interface TaskHandlerProvider {

    /**
     * Get a handler for the given task type.
     *
     * @param taskType the task type (e.g., "http", "shell", "mock")
     * @return the handler for that type
     * @throws IllegalArgumentException if no handler found for type
     */
    TaskHandler getHandler(String taskType);

    /**
     * Get all registered handlers.
     */
    List<TaskHandler> getAllHandlers();
}