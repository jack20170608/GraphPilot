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
 * Simulates task execution with configurable delay.
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
            // Simulate some work
            Thread.sleep(DEFAULT_DELAY.toMillis());

            // 95% success rate for mock
            if (RANDOM.nextDouble() < 0.95) {
                return TaskResult.success("Mock task completed successfully");
            } else {
                return TaskResult.failure("MOCK_ERROR", "Mock task failed randomly");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("Interrupted", e.getMessage());
        }
    }
}