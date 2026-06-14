package com.graphpilot.application.execution.port.in;

import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.dag.TaskDefinition;
import java.util.Map;

/**
 * Interface for executing a task.
 * Implementations handle specific task types (http, shell, mock, etc.).
 */
public interface TaskHandler {

    /**
     * Returns the task type this handler supports.
     */
    String supportedType();

    /**
     * Execute the task and return the result.
     *
     * @param taskRun the task run entity
     * @param task the task definition from workflow
     * @param input input data for the task (can be empty map)
     * @return the task execution result
     */
    TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input);
}