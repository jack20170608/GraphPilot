package com.graphpilot.adapter.worker.handler;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for task handlers.
 * Provides handler lookup by task type.
 */
public class TaskHandlerRegistry implements TaskHandlerProvider {

    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public TaskHandlerRegistry() {
        // Register default handlers
        register(new MockTaskHandler());
        register(new ShellTaskHandler());
    }

    public void register(TaskHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        handlers.put(handler.supportedType(), handler);
    }

    @Override
    public TaskHandler getHandler(String taskType) {
        TaskHandler handler = handlers.get(taskType);
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for task type: " + taskType);
        }
        return handler;
    }

    @Override
    public List<TaskHandler> getAllHandlers() {
        return List.copyOf(handlers.values());
    }
}