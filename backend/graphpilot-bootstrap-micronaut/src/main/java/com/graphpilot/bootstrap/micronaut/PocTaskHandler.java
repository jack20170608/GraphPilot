package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import java.util.Map;

/**
 * Deterministic task handler for the Micronaut runtime PoC.
 *
 * <p>The generic shell/http handlers require task input payloads, which the MVP
 * DAG definition does not yet model. This handler keeps the end-to-end runtime
 * proof deterministic while still exercising the same framework-free worker
 * handler contract and coordinator.
 */
final class PocTaskHandler implements TaskHandler {

    static final String TYPE = "poc";

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        return TaskResult.success("ok:" + taskRun.taskId().value());
    }
}
