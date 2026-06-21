package com.graphpilot.adapter.worker.http;

import com.graphpilot.scheduler.application.execution.port.in.TaskHandler;
import com.graphpilot.scheduler.application.execution.port.in.TaskHandlerProvider;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Task handler provider that dispatches tasks to a remote worker via HTTP.
 * Used by the scheduler when dispatch mode is "remote".
 */
public class HttpRemoteTaskHandlerProvider implements TaskHandlerProvider {

    private final HttpRemoteTaskHandler handler;

    /**
     * Create a new HTTP remote task handler provider.
     *
     * @param workerBaseUrl base URL of the worker process (e.g., "http://localhost:8081")
     * @param timeout HTTP request timeout
     */
    public HttpRemoteTaskHandlerProvider(String workerBaseUrl, Duration timeout) {
        Objects.requireNonNull(workerBaseUrl, "workerBaseUrl must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        this.handler = new HttpRemoteTaskHandler(workerBaseUrl, timeout);
    }

    @Override
    public TaskHandler getHandler(String taskType) {
        // All task types are handled by the same remote handler
        // Task type routing happens on the worker side
        return handler;
    }

    @Override
    public List<TaskHandler> getAllHandlers() {
        // Return empty list - this provider is not used for discovery
        return Collections.emptyList();
    }
}