package com.graphpilot.application.execution.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.application.execution.TaskConfigExpressionException;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TaskConfigExpressionResolver {

    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final String PREFIX = "tasks.";
    private static final String OUTPUT_SEGMENT = ".output";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WorkflowRunRepository workflowRunRepository;

    public TaskConfigExpressionResolver(WorkflowRunRepository workflowRunRepository) {
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository, "workflowRunRepository must not be null");
    }

    public TaskConfig resolve(TaskConfig config, WorkflowRunId workflowRunId) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Map<String, TaskRun> taskRunsByTaskId = workflowRunRepository.findTaskRunsByRunId(workflowRunId).stream()
                .collect(Collectors.toMap(taskRun -> taskRun.taskId().value(), taskRun -> taskRun, (left, right) -> left));
        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) resolveValue(config.asMap(), taskRunsByTaskId);
        return TaskConfig.of(resolved);
    }

    private Object resolveValue(Object value, Map<String, TaskRun> taskRunsByTaskId) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(entry.getKey().toString(), resolveValue(entry.getValue(), taskRunsByTaskId));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object element : list) {
                resolved.add(resolveValue(element, taskRunsByTaskId));
            }
            return List.copyOf(resolved);
        }
        if (value instanceof String text) {
            return resolveString(text, taskRunsByTaskId);
        }
        return value;
    }

    private Object resolveString(String text, Map<String, TaskRun> taskRunsByTaskId) {
        Matcher wholeMatcher = EXPRESSION.matcher(text);
        if (wholeMatcher.matches()) {
            return resolveExpression(wholeMatcher.group(1), taskRunsByTaskId);
        }
        Matcher matcher = EXPRESSION.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = resolveExpression(matcher.group(1), taskRunsByTaskId);
            matcher.appendReplacement(output, Matcher.quoteReplacement(toEmbeddedString(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object resolveExpression(String expression, Map<String, TaskRun> taskRunsByTaskId) {
        if (!expression.startsWith(PREFIX)) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        int outputIndex = expression.indexOf(OUTPUT_SEGMENT, PREFIX.length());
        if (outputIndex <= PREFIX.length()) {
            throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
        }
        String taskId = expression.substring(PREFIX.length(), outputIndex);
        String path = expression.substring(outputIndex + OUTPUT_SEGMENT.length());
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
        Object json = parseOutputJson(taskId, output);
        return resolvePath(json, path.startsWith(".") ? path.substring(1) : invalidPath(path, expression));
    }

    private static String invalidPath(String path, String expression) {
        throw new TaskConfigExpressionException("Invalid task output expression: " + expression);
    }

    private Object parseOutputJson(String taskId, String output) {
        try {
            return JSON.readValue(output, Object.class);
        } catch (JsonProcessingException e) {
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
            cursor = segment.indexOf('[', close + 1);
        }
        return value;
    }

    private String toEmbeddedString(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return JSON.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new TaskConfigExpressionException("Expression result cannot be serialized", e);
            }
        }
        return String.valueOf(value);
    }
}
