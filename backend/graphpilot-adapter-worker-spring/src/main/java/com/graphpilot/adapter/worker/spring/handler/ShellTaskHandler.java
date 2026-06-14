package com.graphpilot.adapter.worker.spring.handler;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell task handler for executing local shell commands.
 */
public class ShellTaskHandler implements TaskHandler {

    private static final String TYPE = "shell";
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        String command = getRequiredString(input, "command");
        long timeoutSeconds = getLong(input, "timeout", DEFAULT_TIMEOUT_SECONDS);
        String workingDir = input.get("workingDir") != null ? input.get("workingDir").toString() : null;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            // Parse command - support both single string and array
            if (command.contains(" ") && !command.startsWith("\"")) {
                // Simple space-separated command
                processBuilder.command(command.split(" "));
            } else {
                processBuilder.command("sh", "-c", command);
            }

            if (workingDir != null) {
                processBuilder.directory(new java.io.File(workingDir));
            }

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Wait for completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return TaskResult.failure("TIMEOUT", "Command timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (exitCode == 0) {
                return TaskResult.success(output.toString().trim());
            } else {
                return TaskResult.failure("EXIT_" + exitCode, output.toString().trim());
            }
        } catch (Exception e) {
            return TaskResult.failure(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String getRequiredString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required input: " + key);
        }
        return value.toString();
    }

    private long getLong(Map<String, Object> input, String key, long defaultValue) {
        Object value = input.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}