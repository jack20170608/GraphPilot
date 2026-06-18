# GraphPilot Task Config Expressions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow downstream task config values to reference upstream task output via a small `${tasks.<taskId>.output...}` JSONPath subset.

**Architecture:** Keep expression parsing in the framework-free application layer before handler execution. The coordinator asks `TaskConfigExpressionResolver` to produce a resolved `TaskConfig`; handlers continue receiving plain input maps and remain unaware of expressions. Expression failures become terminal task failures, do not retry, and write normal task failure/timeline state.

**Tech Stack:** Java 21, Maven multi-module, Jackson already available through application dependencies, JUnit 5/AssertJ/Mockito, existing GraphPilot domain/application ports.

---

## Scope

Implements `docs/superpowers/specs/2026-06-18-graphpilot-task-config-expressions-design.md`.

In scope:

- `${tasks.<taskId>.output}` direct output references.
- JSON object field and array index path access after `.output`, for example `${tasks.extract.output.items[0].id}`.
- Recursive replacement through `TaskConfig` maps/lists.
- Whole-string expressions preserve resolved value type; embedded expressions stringify.
- Expression failure terminally fails current task without calling handler or retrying.
- `mock` handler supports deterministic `output` config for E2E/testing.

Out of scope:

- Full JSONPath filters/wildcards/functions.
- References to fields other than `output`.
- Workflow/run/secret/env variables.
- Schema changes.
- Frontend changes; users already enter config JSON.

## File Structure

### Application expression resolver

- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/TaskConfigExpressionException.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolver.java`
- Test: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolverTest.java`

### Coordinator integration

- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java`
- Modify: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorServiceTest.java`

### Mock handler deterministic output

- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/MockTaskHandler.java`
- Modify: `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/MockTaskHandlerTest.java`

### Integration verification

- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/WorkflowRunApiIntegrationTest.java` or add a focused Spring/Micronaut E2E test if easier after inspecting existing helpers.
- No persistence/API/frontend schema changes expected.

---

## Task 1: Add TaskConfigExpressionResolver

**Files:**
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/TaskConfigExpressionException.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolver.java`
- Create: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolverTest.java`

- [ ] **Step 1: Write failing resolver tests**

Create `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolverTest.java`:

```java
package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.TaskConfigExpressionException;
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
        resolver = new TaskConfigExpressionResolver(repository);
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
        assertThat((List<?>) resolved.asMap().get("items")).containsExactly("x", 42);
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

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) { return Optional.empty(); }
        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) { return List.of(); }
        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) { return List.copyOf(taskRuns); }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=TaskConfigExpressionResolverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `TaskConfigExpressionResolver` and `TaskConfigExpressionException` do not exist.

- [ ] **Step 3: Add exception class**

Create `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/TaskConfigExpressionException.java`:

```java
package com.graphpilot.application.execution;

public final class TaskConfigExpressionException extends RuntimeException {
    public TaskConfigExpressionException(String message) {
        super(message);
    }

    public TaskConfigExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Implement resolver**

Create `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolver.java`:

```java
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
            int arrayIndex = Integer.parseInt(segment.substring(cursor + 1, close));
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
```

- [ ] **Step 5: Run resolver tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=TaskConfigExpressionResolverTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Run application tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/TaskConfigExpressionException.java \
        backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolver.java \
        backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/TaskConfigExpressionResolverTest.java
git commit -m "feat(worker): add task config expression resolver"
```

---

## Task 2: Integrate resolver with coordinator failure behavior

**Files:**
- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java`
- Modify: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorServiceTest.java`

- [ ] **Step 1: Write failing coordinator tests**

In `WorkflowExecutionCoordinatorServiceTest`, extend setup to use a real resolver:

```java
coordinator = new WorkflowExecutionCoordinatorService(
        workflowRepository,
        workflowRunRepository,
        taskHandlerProvider,
        fixedClock(),
        backoffStrategy,
        TimelineRecorder.noop(fixedClock()),
        new TaskConfigExpressionResolver(workflowRunRepository));
```

Add test:

```java
@Test
void executePassesResolvedTaskConfigToHandler() {
    WorkflowRunId runId = WorkflowRunId.of("run-1");
    WorkflowId workflowId = WorkflowId.of("workflow-1");
    workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
    workflowRunRepository.storeTaskRun(createTaskRun(runId, "extract", TaskRunStatus.SUCCEEDED, 0).withOutput("{\"id\":\"abc\"}"));
    workflowRunRepository.storeTaskRun(createTaskRun(runId, "load", TaskRunStatus.PENDING, 1));
    givenWorkflow(workflowId, new DagDefinition(
            List.of(
                    new TaskDefinition(TaskId.of("extract"), "Extract", "mock"),
                    new TaskDefinition(
                            TaskId.of("load"),
                            "Load",
                            "mock",
                            TaskConfig.of(Map.of("value", "${tasks.extract.output.id}")))),
            List.of(new DagEdge(TaskId.of("extract"), TaskId.of("load")))));

    coordinator.execute(runId);

    assertThat(taskHandler.receivedInputs).contains(Map.of("value", "abc"));
}
```

Add failure test:

```java
@Test
void executeFailsTaskWithoutRetryWhenExpressionResolutionFails() {
    WorkflowRunId runId = WorkflowRunId.of("run-1");
    WorkflowId workflowId = WorkflowId.of("workflow-1");
    workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
    workflowRunRepository.storeTaskRun(createTaskRun(runId, "load", TaskRunStatus.PENDING, 0));
    givenWorkflow(workflowId, new DagDefinition(
            List.of(new TaskDefinition(
                    TaskId.of("load"),
                    "Load",
                    "mock",
                    TaskConfig.of(Map.of("value", "${tasks.missing.output}")))),
            List.of()));

    coordinator.execute(runId);

    TaskRun failed = workflowRunRepository.findTaskRunsByRunId(runId).getFirst();
    assertThat(taskHandler.invocations).isEmpty();
    assertThat(failed.status()).isEqualTo(TaskRunStatus.FAILED);
    assertThat(failed.retryCount()).isZero();
    assertThat(failed.errorMessage()).contains("Task config expression failed");
    assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status()).isEqualTo(WorkflowRunStatus.FAILED);
}
```

- [ ] **Step 2: Run tests and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=WorkflowExecutionCoordinatorServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because coordinator does not resolve expressions.

- [ ] **Step 3: Add resolver field and constructors**

In `WorkflowExecutionCoordinatorService`, add:

```java
private final TaskConfigExpressionResolver expressionResolver;
```

Update constructors so existing ones delegate to:

```java
this(..., TimelineRecorder.noop(clock), new TaskConfigExpressionResolver(workflowRunRepository));
```

Main constructor:

```java
public WorkflowExecutionCoordinatorService(
        WorkflowRepository workflowRepository,
        WorkflowRunRepository workflowRunRepository,
        TaskHandlerProvider taskHandlerProvider,
        ClockPort clock,
        BackoffStrategy backoffStrategy,
        TimelineRecorder timelineRecorder,
        TaskConfigExpressionResolver expressionResolver) {
    ...
    this.expressionResolver = Objects.requireNonNull(expressionResolver, "expressionResolver must not be null");
}
```

- [ ] **Step 4: Resolve config before handler execution**

In `executeTask`, before marking task RUNNING, add:

```java
TaskConfig resolvedConfig;
try {
    resolvedConfig = expressionResolver.resolve(taskDef.config(), taskRun.workflowRunId());
} catch (TaskConfigExpressionException e) {
    failTaskForExpressionError(taskRun, e.getMessage());
    return;
}
```

Then change handler call:

```java
result = handler.execute(taskRun, taskDef, resolvedConfig.asMap());
```

Add helper:

```java
private void failTaskForExpressionError(TaskRun taskRun, String message) {
    Instant now = clock.now();
    TaskRun failed = taskRun.withStatus(TaskRunStatus.FAILED)
            .withFinishedAt(now)
            .withErrorMessage("Task config expression failed: " + message)
            .withOutput(null);
    workflowRunRepository.updateTaskRunStatus(taskRun.workflowRunId(), failed);
    timelineRecorder.task(taskRun.workflowRunId(), taskRun.id(), taskRun.taskId(),
            TimelineEventType.TASK_FAILED,
            "Task " + taskRun.taskId().value() + " failed while resolving config");
}
```

Do not call `backoffStrategy` in this path.

- [ ] **Step 5: Run application tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java \
        backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorServiceTest.java
git commit -m "feat(worker): resolve task config expressions before execution"
```

---

## Task 3: Support deterministic mock output and add E2E expression test

**Files:**
- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/MockTaskHandler.java`
- Modify: `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/MockTaskHandlerTest.java`
- Modify or add: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/WorkflowRunApiIntegrationTest.java`

- [ ] **Step 1: Write failing mock output test**

Add to `MockTaskHandlerTest`:

```java
@Test
void executeUsesConfiguredOutputWhenForcedToSucceed() {
    TaskResult result = handler.execute(
            createTaskRun(TaskRunStatus.PENDING),
            createTaskDefinition(),
            Map.of("success", true, "delayMs", 0, "output", "{\"id\":\"abc\"}"));

    assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
    assertThat(result.output()).contains("{\"id\":\"abc\"}");
}
```

- [ ] **Step 2: Run worker tests and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-worker -am test -Dtest=MockTaskHandlerTest
```

Expected: FAIL because mock ignores `output`.

- [ ] **Step 3: Implement mock output config**

In `MockTaskHandler`, deterministic success branch becomes:

```java
if (success instanceof Boolean fixedSuccess) {
    if (fixedSuccess) {
        return TaskResult.success(input.getOrDefault("output", "Mock task completed successfully").toString());
    }
    return TaskResult.failure("MOCK_ERROR", "Mock task failed by config");
}
```

- [ ] **Step 4: Run worker tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-worker -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Add Spring E2E expression test**

Add a new test in `WorkflowRunApiIntegrationTest`:

```java
@Test
void resolvesTaskConfigExpressionFromUpstreamOutput() {
    String requestBody = """
            {
              "name": "Expression ETL",
              "tasks": [
                {
                  "id": "extract",
                  "name": "Extract",
                  "type": "mock",
                  "config": { "success": true, "delayMs": 0, "output": "{\\\"id\\\":\\\"abc\\\"}" }
                },
                {
                  "id": "load",
                  "name": "Load",
                  "type": "mock",
                  "config": { "success": true, "delayMs": 0, "output": "loaded ${tasks.extract.output.id}" }
                }
              ],
              "edges": [
                { "fromTaskId": "extract", "toTaskId": "load" }
              ]
            }
            """;

    ResponseEntity<Map<String, Object>> createWorkflowResponse = postWorkflow(restTemplate, requestBody);
    assertThat(createWorkflowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String workflowId = createWorkflowResponse.getBody().get("id").toString();
    assertThat(activateWorkflow(restTemplate, workflowId).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map<String, Object>> triggerResponse = triggerWorkflowRun(restTemplate, workflowId);
    String runId = triggerResponse.getBody().get("id").toString();
    Map<String, Object> finalRun = awaitWorkflowRunTerminal(restTemplate, runId);
    assertWorkflowRun(finalRun, runId, workflowId, "SUCCEEDED");

    ResponseEntity<List<Map<String, Object>>> taskRunsResponse = listTaskRuns(restTemplate, runId);
    assertThat(taskRunsResponse.getBody())
            .filteredOn(task -> task.get("taskId").equals("load"))
            .singleElement()
            .satisfies(task -> assertThat(task.get("output")).isEqualTo("loaded abc"));
}
```

If string escaping is too brittle, build the JSON using `Map` and `TestRestTemplate.exchange`; keep the assertion identical.

- [ ] **Step 6: Run Spring integration test**

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=WorkflowRunApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-adapter-worker backend/graphpilot-bootstrap-spring
git commit -m "test(worker): verify config expressions end-to-end"
```

---

## Task 4: Documentation and final verification

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture/overview.md`
- Optionally modify: `docs/superpowers/specs/2026-06-18-graphpilot-task-config-expressions-design.md` only if implementation behavior differs from spec.

- [ ] **Step 1: Run backend full tests**

```bash
mvn -f backend/pom.xml test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Update README**

Add one bullet in current status section:

```markdown
- Task config supports `${tasks.<taskId>.output...}` expressions for passing upstream task output into downstream handler input. The first version supports object fields and array indexes over JSON output strings.
```

- [ ] **Step 3: Update architecture overview**

Add to worker capability paragraph:

```markdown
Task config expressions are resolved in the application worker coordinator before handler execution, so handlers receive plain resolved config maps and remain expression-agnostic.
```

- [ ] **Step 4: Run frontend checks**

No frontend code is expected to change, but run:

```bash
cd apps/web
npx tsc --noEmit
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 5: Commit docs**

```bash
git add README.md docs/architecture/overview.md docs/superpowers/specs/2026-06-18-graphpilot-task-config-expressions-design.md
git commit -m "docs: document task config expressions"
```

- [ ] **Step 6: Push**

```bash
git push origin main
```

Expected: push succeeds.

---

## Self-Review

### Spec coverage

- Expression syntax and JSONPath subset: Task 1.
- Recursive replacement and type preservation: Task 1.
- Coordinator failure behavior and no retry: Task 2.
- Mock deterministic output and E2E proof: Task 3.
- Documentation and verification: Task 4.

### Placeholder scan

No placeholder markers are intentionally left in this plan. Each task contains concrete files, commands, expected results, and implementation snippets.

### Type consistency

- `TaskConfigExpressionResolver.resolve(TaskConfig, WorkflowRunId)` is introduced before coordinator uses it.
- `TaskConfigExpressionException` is introduced before coordinator catches it.
- Existing `TaskConfig`, `TaskRun.output`, `TimelineRecorder`, and `WorkflowRunRepository` names match the current codebase.
