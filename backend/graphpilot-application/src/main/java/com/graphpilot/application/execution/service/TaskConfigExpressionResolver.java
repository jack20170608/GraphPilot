package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.TaskConfigExpressionException;
import com.graphpilot.application.execution.port.out.JsonValueCodecPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskConfigExpressionResolver {

    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final String PREFIX = "tasks.";
    private static final String OUTPUT_SEGMENT = ".output";

    private final WorkflowRunRepository workflowRunRepository;
    private final JsonValueCodecPort jsonCodec;

    public TaskConfigExpressionResolver(WorkflowRunRepository workflowRunRepository,
            JsonValueCodecPort jsonCodec) {
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository, "workflowRunRepository must not be null");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
    }

    public TaskConfig resolve(TaskConfig config, WorkflowRunId workflowRunId) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        if (!containsExpression(config.asMap())) {
            return config;
        }
        Map<String, TaskRun> taskRunsByTaskId = toMap(workflowRunRepository.findTaskRunsByRunId(workflowRunId));
        Map<String, Object> jsonCache = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) resolveValue(config.asMap(), taskRunsByTaskId, jsonCache);
        return TaskConfig.of(resolved);
    }

    private static Map<String, TaskRun> toMap(List<TaskRun> taskRuns) {
        Map<String, TaskRun> map = new LinkedHashMap<>();
        for (TaskRun taskRun : taskRuns) {
            String key = taskRun.taskId().value();
            if (map.containsKey(key)) {
                throw new TaskConfigExpressionException("Duplicate task ID in workflow run: " + key);
            }
            map.put(key, taskRun);
        }
        return map;
    }

    private static boolean containsExpression(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                if (containsExpression(entryValue)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (containsExpression(element)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof String text) {
            return text.contains("${");
        }
        return false;
    }

    private Object resolveValue(Object value, Map<String, TaskRun> taskRunsByTaskId, Map<String, Object> jsonCache) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(entry.getKey().toString(), resolveValue(entry.getValue(), taskRunsByTaskId, jsonCache));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object element : list) {
                resolved.add(resolveValue(element, taskRunsByTaskId, jsonCache));
            }
            return List.copyOf(resolved);
        }
        if (value instanceof String text) {
            return resolveString(text, taskRunsByTaskId, jsonCache);
        }
        return value;
    }

    private Object resolveString(String text, Map<String, TaskRun> taskRunsByTaskId, Map<String, Object> jsonCache) {
        Matcher wholeMatcher = EXPRESSION.matcher(text);
        if (wholeMatcher.matches()) {
            return resolveExpression(wholeMatcher.group(1), taskRunsByTaskId, jsonCache);
        }
        Matcher matcher = EXPRESSION.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = resolveExpression(matcher.group(1), taskRunsByTaskId, jsonCache);
            matcher.appendReplacement(output, Matcher.quoteReplacement(toEmbeddedString(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object resolveExpression(String expression, Map<String, TaskRun> taskRunsByTaskId, Map<String, Object> jsonCache) {
        if (!expression.startsWith(PREFIX)) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        int outputIndex = expression.indexOf(OUTPUT_SEGMENT, PREFIX.length());
        if (outputIndex <= PREFIX.length()) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        String taskId = expression.substring(PREFIX.length(), outputIndex);
        String path = expression.substring(outputIndex + OUTPUT_SEGMENT.length());
        if (path.endsWith(".")) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        TaskRun taskRun = taskRunsByTaskId.get(taskId);
        if (taskRun == null) {
            throw new TaskConfigExpressionException("Referenced task not found: " + taskId);
        }
        if (taskRun.status() != TaskRunStatus.SUCCEEDED) {
            throw new TaskConfigExpressionException("Referenced task is not SUCCEEDED: " + taskId);
        }
        String output = taskRun.output();
        if (output == null) {
            throw new TaskConfigExpressionException("Referenced task output is empty: " + taskId);
        }
        if (path.isEmpty()) {
            return output;
        }
        if (!path.startsWith(".")) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        String normalizedPath = path.substring(1);
        Object json = jsonCache.computeIfAbsent(taskId, k -> parseOutputJson(taskId, output));
        return resolvePath(json, normalizedPath);
    }

    private Object parseOutputJson(String taskId, String output) {
        try {
            return jsonCodec.parse(output);
        } catch (RuntimeException e) {
            throw new TaskConfigExpressionException("Task output is not valid JSON: " + taskId, e);
        }
    }

    private Object resolvePath(Object current, String path) {
        int index = 0;
        while (index < path.length()) {
            int nextDot = path.indexOf('.', index);
            String segment = nextDot == -1 ? path.substring(index) : path.substring(index, nextDot);
            current = resolveSegment(current, segment);
            index = nextDot == -1 ? path.length() : nextDot + 1;
        }
        return current;
    }

    private Object resolveSegment(Object current, String segment) {
        if (segment.isBlank()) {
            throw new TaskConfigExpressionException("Invalid empty path segment");
        }
        int bracketIndex = segment.indexOf('[');
        String field = bracketIndex == -1 ? segment : segment.substring(0, bracketIndex);
        Object value = current;
        if (!field.isBlank()) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(field)) {
                throw new TaskConfigExpressionException("Path does not exist: " + field);
            }
            value = map.get(field);
        }
        int cursor = bracketIndex;
        int lastProcessedClose = -1;
        while (cursor != -1) {
            int close = segment.indexOf(']', cursor);
            if (close == -1 || close == cursor + 1) {
                throw new TaskConfigExpressionException("Invalid array index in path segment: " + segment);
            }
            int arrayIndex;
            try {
                arrayIndex = Integer.parseInt(segment.substring(cursor + 1, close));
            } catch (NumberFormatException e) {
                throw new TaskConfigExpressionException("Invalid array index in path segment: " + segment);
            }
            if (!(value instanceof List<?> list)) {
                throw new TaskConfigExpressionException("Path segment is not an array: " + segment);
            }
            if (arrayIndex < 0 || arrayIndex >= list.size()) {
                throw new TaskConfigExpressionException("Array index out of bounds: " + arrayIndex);
            }
            value = list.get(arrayIndex);
            lastProcessedClose = close;
            cursor = segment.indexOf('[', close + 1);
        }
        if (bracketIndex != -1 && lastProcessedClose + 1 < segment.length()) {
            throw new TaskConfigExpressionException("Invalid path segment: " + segment);
        }
        return value;
    }

    private String toEmbeddedString(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return jsonCodec.stringify(value);
            } catch (RuntimeException e) {
                throw new TaskConfigExpressionException("Expression result cannot be serialized", e);
            }
        }
        if (value == null) {
            return "null";
        }
        return String.valueOf(value);
    }
}
