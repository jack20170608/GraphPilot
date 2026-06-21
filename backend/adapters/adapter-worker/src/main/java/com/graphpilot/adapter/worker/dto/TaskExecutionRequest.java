package com.graphpilot.adapter.worker.dto;

import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskRun;
import java.util.Map;

/**
 * Request DTO for worker task execution.
 * Sent from scheduler to worker when dispatching a task.
 *
 * @param taskRun the task run entity with ID, status, retry info
 * @param taskDefinition the task definition from workflow DAG
 * @param config resolved config map (already expression-resolved by scheduler)
 */
public record TaskExecutionRequest(
        TaskRun taskRun,
        TaskDefinition taskDefinition,
        Map<String, Object> config) {

    public TaskExecutionRequest {
        if (taskRun == null) {
            throw new IllegalArgumentException("taskRun must not be null");
        }
        if (taskDefinition == null) {
            throw new IllegalArgumentException("taskDefinition must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
    }
}