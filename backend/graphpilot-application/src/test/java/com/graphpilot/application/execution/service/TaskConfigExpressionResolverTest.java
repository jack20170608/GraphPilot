package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.application.execution.TaskConfigExpressionException;
import com.graphpilot.application.execution.port.out.JsonValueCodecPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskConfigExpressionResolverTest {

    private static final WorkflowRunId RUN_ID = WorkflowRunId.of("run-1");
    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");

    private FakeWorkflowRunRepository repository;
    private TaskConfigExpressionResolver resolver;

    @BeforeEach
    void setUp() {
        repository = new FakeWorkflowRunRepository();
        resolver = new TaskConfigExpressionResolver(repository, new JacksonJsonValueCodec());
    }

    private static final class JacksonJsonValueCodec implements JsonValueCodecPort {
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public Object parse(String json) {
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse JSON", e);
            }
        }

        @Override
        public String stringify(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to stringify value to JSON", e);
            }
        }
    }

    @Test
    void resolvesDirectOutputString() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "hello"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("body", "${tasks.extract.output}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("body", "hello");
    }

    @Test
    void resolvesJsonObjectField() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"id\":\"abc\"}"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.id}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("id", "abc");
    }

    @Test
    void resolvesNestedArrayField() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"items\":[{\"id\":\"first\"}]}"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[0].id}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("id", "first");
    }

    @Test
    void wholeStringExpressionPreservesNumberBooleanMapAndListTypes() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"timeout\":10,\"enabled\":true,\"meta\":{\"x\":1},\"items\":[\"a\"]}"));

        TaskConfig resolved = resolver.resolve(TaskConfig.of(Map.of(
                "timeout", "${tasks.extract.output.timeout}",
                "enabled", "${tasks.extract.output.enabled}",
                "meta", "${tasks.extract.output.meta}",
                "items", "${tasks.extract.output.items}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("timeout", 10);
        assertThat(resolved.asMap()).containsEntry("enabled", true);
        assertThat(resolved.asMap().get("meta")).isInstanceOf(Map.class);
        assertThat(resolved.asMap().get("items")).isInstanceOf(List.class);
    }

    @Test
    void embeddedExpressionsAreStringifiedAndReplacedInOrder() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"id\":\"abc\",\"name\":\"demo\"}"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("command", "echo ${tasks.extract.output.id}:${tasks.extract.output.name}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("command", "echo abc:demo");
    }

    @Test
    void recursivelyResolvesMapsAndLists() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"id\":\"abc\",\"tags\":[\"x\"]}"));

        TaskConfig resolved = resolver.resolve(TaskConfig.of(Map.of(
                "nested", Map.of("id", "${tasks.extract.output.id}"),
                "items", List.of("${tasks.extract.output.tags[0]}", 42))), RUN_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) resolved.asMap().get("nested");
        assertThat(nested).containsEntry("id", "abc");
        assertThat((List<Object>) resolved.asMap().get("items")).containsExactly("x", 42);
    }

    @Test
    void leavesNonStringValuesUnchanged() {
        TaskConfig resolved = resolver.resolve(TaskConfig.of(Map.of(
                "n", 1,
                "flag", true)), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("n", 1).containsEntry("flag", true);
    }

    @Test
    void failsWhenReferencedTaskDoesNotExist() {
        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.missing.output}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Referenced task not found: missing");
    }

    @Test
    void failsWhenReferencedTaskIsNotSucceeded() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.FAILED, "{}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Referenced task is not SUCCEEDED: extract");
    }

    @Test
    void failsWhenOutputIsNull() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, null));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Referenced task output is empty: extract");
    }

    @Test
    void failsWhenSubPathRequiresJsonButOutputIsPlainText() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "hello"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.id}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Task output is not valid JSON: extract");
    }

    @Test
    void failsWhenPathDoesNotExist() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"id\":\"abc\"}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.missing}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Path does not exist: missing");
    }

    @Test
    void failsWhenArrayIndexIsOutOfBounds() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"items\":[]}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[0]}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Array index out of bounds: 0");
    }

    @Test
    void failsForInvalidExpressionSyntax() {
        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${task.extract.output}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid task output expression");
    }

    @Test
    void failsWhenArrayIndexIsInvalid() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"items\":[\"a\"]}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[abc]}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid array index in path segment: items[abc]");
    }

    @Test
    void failsWhenPathSegmentHasTrailingCharactersAfterBracket() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"items\":[\"a\"]}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[0]abc}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid path segment: items[0]abc");
    }

    @Test
    void failsWhenPathSegmentHasExtraClosingBracket() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"items\":[\"a\"]}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[0]]}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid path segment: items[0]]");
    }

    @Test
    void failsWhenPathSegmentHasTrailingCharactersBeforeExtraClosingBracket() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"items\":[\"a\"]}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.items[0]abc]}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid path segment: items[0]abc]");
    }

    @Test
    void failsWhenExpressionEndsWithTrailingDot() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"id\":\"abc\"}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Invalid task output expression: tasks.extract.output.");
    }

    @Test
    void failsWhenDuplicateTaskIdsInWorkflowRun() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"id\":\"a\"}"));
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED, "{\"id\":\"b\"}"));

        assertThatThrownBy(() -> resolver.resolve(
                TaskConfig.of(Map.of("id", "${tasks.extract.output.id}")), RUN_ID))
                .isInstanceOf(TaskConfigExpressionException.class)
                .hasMessageContaining("Duplicate task ID in workflow run: extract");
    }

    @Test
    void resolvesConsecutiveArrayIndexes() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"matrix\":[[\"a\",\"b\"],[\"c\",\"d\"]]}"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("value", "${tasks.extract.output.matrix[1][0]}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("value", "c");
    }

    @Test
    void wholeStringExpressionReturnsNullForJsonNull() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"result\":null}"));

        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("value", "${tasks.extract.output.result}");
        TaskConfig resolved = resolver.resolve(TaskConfig.of(map), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("value", null);
    }

    @Test
    void embeddedExpressionStringifiesJsonNullToNullLiteral() {
        repository.taskRuns.add(taskRun("extract", TaskRunStatus.SUCCEEDED,
                "{\"result\":null}"));

        TaskConfig resolved = resolver.resolve(
                TaskConfig.of(Map.of("value", "result=${tasks.extract.output.result}")), RUN_ID);

        assertThat(resolved.asMap()).containsEntry("value", "result=null");
    }

    @Test
    void returnsOriginalConfigWithoutQueryingRepositoryWhenNoExpressions() {
        TaskConfig config = TaskConfig.of(Map.of("n", 1, "flag", true));

        TaskConfig resolved = resolver.resolve(config, RUN_ID);

        assertThat(resolved).isSameAs(config);
        assertThat(repository.queryCount).isZero();
    }

    private static TaskRun taskRun(String taskId, TaskRunStatus status, String output) {
        return TaskRun.restore(
                TaskRunId.of("task-run-" + taskId),
                RUN_ID,
                TaskId.of(taskId),
                "Task " + taskId,
                "mock",
                status,
                0,
                0,
                3,
                null,
                output,
                NOW,
                NOW,
                NOW);
    }

    private static final class FakeWorkflowRunRepository implements WorkflowRunRepository {
        final List<TaskRun> taskRuns = new ArrayList<>();
        int queryCount = 0;

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) { return Optional.empty(); }
        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) { return List.of(); }
        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) { queryCount++; return List.copyOf(taskRuns); }
    }
}
