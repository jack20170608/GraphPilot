package com.graphpilot.adapter.worker.handler;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import java.time.Duration;
import java.util.Map;
import java.util.Random;

/**
 * Mock task handler for testing and development.
 * Simulates task execution with configurable delay and deterministic success/failure when requested.
 */
public class MockTaskHandler implements TaskHandler {

    private static final String TYPE = "mock";
    private static final Duration DEFAULT_DELAY = Duration.ofMillis(100);
    private static final Random RANDOM = new Random();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        try {
            long delayMs = getLong(input, "delayMs", DEFAULT_DELAY.toMillis());
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }

            Object success = input.get("success");
            if (success instanceof Boolean fixedSuccess) {
                if (fixedSuccess) {
                    return TaskResult.success(input.getOrDefault("output", "Mock task completed successfully").toString());
                }
                return TaskResult.failure("MOCK_ERROR", "Mock task failed by config");
            }

            // 95% success rate for mock when no deterministic config is provided.
            if (RANDOM.nextDouble() < 0.95) {
                return TaskResult.success("Mock task completed successfully");
            }
            return TaskResult.failure("MOCK_ERROR", "Mock task failed randomly");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("Interrupted", e.getMessage());
        }
    }

    private long getLong(Map<String, Object> input, String key, long defaultValue) {
        Object value = input.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
