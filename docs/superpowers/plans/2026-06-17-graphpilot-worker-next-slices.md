# GraphPilot Worker Next Slices Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the next worker milestone: static task config, pending-run scanner, structured timeline, Micronaut API parity, and frontend timeline/config support.

**Architecture:** Keep domain/application framework-free. Add small domain value objects and application ports, then adapt memory/MyBatis, Spring Web, Micronaut bootstrap, and frontend in vertical slices. Scanner only executes PENDING runs; timeline is append-only structured events, not a text log stream.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot Web adapter, Micronaut runtime PoC, MyBatis XML, Flyway, JUnit 5/AssertJ/Mockito, Next.js 16, TypeScript, TanStack Query, React Flow, shadcn/Base UI patterns.

---

## Scope Notes

This plan implements the approved design in `docs/superpowers/specs/2026-06-17-graphpilot-worker-next-slices-design.md` as one continuous milestone. It intentionally does **not** implement upstream-output references, expression templates, RUNNING timeout recovery, or full handler text logs.

## File Structure

### Domain

- Modify: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskDefinition.java` — add `TaskConfig` field and compatible constructors.
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskConfig.java` — immutable wrapper around static JSON config.
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TimelineEventId.java` — value object for timeline IDs.
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TimelineEventType.java` — event type enum.
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunTimelineEvent.java` — append-only timeline event.
- Test: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskConfigTest.java`
- Test: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskDefinitionTest.java`
- Test: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/execution/WorkflowRunTimelineEventTest.java`

### Application

- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java` — pass task config to handlers and write timeline events.
- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TriggerWorkflowRunService.java` — write `RUN_CREATED` timeline event.
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/ScanPendingWorkflowRunsUseCase.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/ScanPendingWorkflowRunsService.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/ScanResult.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/ScanFailure.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/TimelineEventIdGeneratorPort.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/WorkflowRunTimelineRepository.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TimelineRecorder.java`
- Test: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorServiceTest.java`
- Test: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/ScanPendingWorkflowRunsServiceTest.java`
- Test: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/TriggerWorkflowRunServiceTest.java`

### Worker Core

- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/ShellTaskHandler.java` — read `command`, `timeout`, `workingDir` from task config map.
- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/HttpTaskHandler.java` — read `url`, `method`, `headers`, `body` from task config map.
- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/MockTaskHandler.java` — support `delayMs` and deterministic `success` config.
- Test: existing handler tests under `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/`.

### Persistence Memory

- Modify: `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunRepository.java` — add timeline repository behavior or create separate timeline repository class.
- Create: `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunTimelineRepository.java`
- Create: `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidTimelineEventIdGenerator.java`
- Test: `backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunTimelineRepositoryTest.java`

### Persistence MyBatis

- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql` only if test fixtures rebuild from V1 is required; otherwise add V5.
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V5__add_task_config_and_timeline_events.sql`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowTaskRow.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/TaskRunRow.java` only if timeline relation requires no change.
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRunTimelineEventRow.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.java` — add `findWorkflowRunsByStatus`.
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.xml`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunTimelineRepository.java`
- Test: existing MyBatis tests plus new timeline/config integration tests.

### Spring Web / Bootstrap

- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/CreateWorkflowRequest.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowResponse.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowController.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunController.java` — add timeline endpoint.
- Create: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/TimelineEventResponse.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowRunAssemblyConfiguration.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerAssemblyConfiguration.java`
- Create: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerScannerScheduler.java`
- Test: controller/bootstrap integration tests.

### Micronaut

- Modify: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkflowRunController.java` — replace PoC routes with API parity routes or split into workflow/run controllers.
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkflowController.java`
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/MicronautErrorHandler.java`
- Modify: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/GraphPilotFactory.java`
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkerScannerScheduler.java`
- Test: `backend/graphpilot-bootstrap-micronaut/src/test/java/com/graphpilot/bootstrap/micronaut/WorkflowRunEndToEndTest.java`
- Test: new parity tests under `backend/graphpilot-bootstrap-micronaut/src/test/java/com/graphpilot/bootstrap/micronaut/`.

### Frontend

- Modify: `apps/web/src/lib/types/index.ts` — add task config and timeline event types.
- Modify: `apps/web/src/lib/api/workflows.ts` — create/list/get workflow payloads include config.
- Modify: `apps/web/src/lib/api/workflow-runs.ts` — add `listTimelineEvents`.
- Modify: `apps/web/src/hooks/use-workflow-runs.ts` — add `useWorkflowRunTimeline` polling.
- Modify: `apps/web/src/app/(console)/workflow-runs/[id]/page.tsx` — add Timeline tab.
- Create: `apps/web/src/components/run/workflow-run-timeline.tsx`
- Modify: `apps/web/src/components/run/task-run-table.tsx` if timeline links to task IDs.
- Modify: workflow create UI under `apps/web/src/app/(console)/workflows/new/page.tsx` and related components if present.

---

## Task 1: Add TaskConfig domain model

**Files:**
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskConfig.java`
- Modify: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskDefinition.java`
- Create: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskConfigTest.java`
- Modify: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskDefinitionTest.java`

- [ ] **Step 1: Write failing TaskConfig tests**

Create `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskConfigTest.java`:

```java
package com.graphpilot.domain.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskConfigTest {

    @Test
    void emptyReturnsImmutableEmptyConfig() {
        TaskConfig config = TaskConfig.empty();

        assertThat(config.asMap()).isEmpty();
        assertThatThrownBy(() -> config.asMap().put("command", "echo hi"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesInputMapDefensively() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("command", "echo hi");

        TaskConfig config = TaskConfig.of(values);
        values.put("command", "rm -rf /tmp/noop");

        assertThat(config.getString("command")).contains("echo hi");
    }

    @Test
    void rejectsNullMap() {
        assertThatThrownBy(() -> TaskConfig.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("values must not be null");
    }

    @Test
    void readsStringAndLongValues() {
        TaskConfig config = TaskConfig.of(Map.of(
                "command", "echo hi",
                "timeout", 10));

        assertThat(config.getString("command")).contains("echo hi");
        assertThat(config.getLong("timeout")).contains(10L);
        assertThat(config.getString("missing")).isEmpty();
        assertThat(config.getLong("missing")).isEmpty();
    }
}
```

- [ ] **Step 2: Run domain tests and verify fail**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=TaskConfigTest
```

Expected: compilation fails because `TaskConfig` does not exist.

- [ ] **Step 3: Implement TaskConfig**

Create `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskConfig.java`:

```java
package com.graphpilot.domain.dag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TaskConfig(Map<String, Object> values) {

    public TaskConfig {
        Objects.requireNonNull(values, "values must not be null");
        values = Map.copyOf(new LinkedHashMap<>(values));
    }

    public static TaskConfig empty() {
        return new TaskConfig(Map.of());
    }

    public static TaskConfig of(Map<String, Object> values) {
        return new TaskConfig(values);
    }

    public Optional<Object> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(values.get(key));
    }

    public Optional<String> getString(String key) {
        return get(key).map(Object::toString);
    }

    public Optional<Long> getLong(String key) {
        return get(key).map(value -> {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(value.toString());
        });
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
```

- [ ] **Step 4: Run TaskConfig tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=TaskConfigTest
```

Expected: PASS.

- [ ] **Step 5: Update TaskDefinition tests**

Modify `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskDefinitionTest.java` to include:

```java
@Test
void defaultsConfigToEmpty() {
    TaskDefinition task = new TaskDefinition(TaskId.of("extract"), "Extract data");

    assertThat(task.config()).isEqualTo(TaskConfig.empty());
}

@Test
void acceptsStaticConfig() {
    TaskDefinition task = new TaskDefinition(
            TaskId.of("extract"),
            "Extract data",
            "shell",
            TaskConfig.of(Map.of("command", "echo hi")));

    assertThat(task.type()).isEqualTo("shell");
    assertThat(task.config().getString("command")).contains("echo hi");
}
```

Add imports if missing:

```java
import java.util.Map;
```

- [ ] **Step 6: Update TaskDefinition implementation**

Modify `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskDefinition.java` to:

```java
package com.graphpilot.domain.dag;

import java.util.Objects;

public record TaskDefinition(TaskId id, String name, String type, TaskConfig config) {

    public static final String DEFAULT_TYPE = "mock";

    public TaskDefinition(TaskId id, String name) {
        this(id, name, DEFAULT_TYPE, TaskConfig.empty());
    }

    public TaskDefinition(TaskId id, String name, String type) {
        this(id, name, type, TaskConfig.empty());
    }

    public TaskDefinition {
        Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }
        name = name.trim();
        type = (type == null || type.isBlank()) ? DEFAULT_TYPE : type.trim();
        config = config == null ? TaskConfig.empty() : config;
    }

    public static TaskDefinition of(TaskId id, String name) {
        return new TaskDefinition(id, name, DEFAULT_TYPE, TaskConfig.empty());
    }

    public static TaskDefinition of(TaskId id, String name, String type) {
        return new TaskDefinition(id, name, type, TaskConfig.empty());
    }

    public static TaskDefinition of(TaskId id, String name, String type, TaskConfig config) {
        return new TaskDefinition(id, name, type, config);
    }
}
```

- [ ] **Step 7: Run all domain tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskConfig.java \
        backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag/TaskDefinition.java \
        backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskConfigTest.java \
        backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag/TaskDefinitionTest.java
git commit -m "feat(domain): add static task config model"
```

---

## Task 2: Persist workflow task config in memory and MyBatis

**Files:**
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowTaskRow.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V5__add_task_config_and_timeline_events.sql` (timeline table added in Task 7; config column added now)
- Test: `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing MyBatis config round-trip test**

Add to `MyBatisWorkflowRepositoryIntegrationTest`:

```java
@Test
void savesAndLoadsTaskConfig() {
    Workflow workflow = Workflow.create(
            WorkflowId.of("workflow-config"),
            new WorkflowName("Config Workflow"),
            new DagDefinition(
                    List.of(new TaskDefinition(
                            TaskId.of("shell"),
                            "Shell task",
                            "shell",
                            TaskConfig.of(Map.of("command", "echo hi", "timeout", 10)))),
                    List.of()),
            Instant.parse("2026-06-17T00:00:00Z"));

    repository.save(workflow);

    Workflow found = repository.findById(workflow.id()).orElseThrow();
    TaskDefinition task = found.dag().tasks().get(0);
    assertThat(task.config().getString("command")).contains("echo hi");
    assertThat(task.config().getLong("timeout")).contains(10L);
}
```

Add imports:

```java
import com.graphpilot.domain.dag.TaskConfig;
import java.util.Map;
```

- [ ] **Step 2: Run test and verify fail**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRepositoryIntegrationTest
```

Expected: FAIL because config is not persisted or migration column is missing.

- [ ] **Step 3: Create V5 migration with config column**

Create `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V5__add_task_config_and_timeline_events.sql` with only config for now:

```sql
-- V5: task config and workflow run timeline events
-- Config column is used by task handlers as static JSON input.

ALTER TABLE workflow_tasks ADD COLUMN config jsonb NOT NULL DEFAULT '{}';
```

Timeline table will be appended to this same V5 file in Task 7 before the final commit for that task. If Task 7 is implemented in a separate commit after this, add the timeline table as `V6__create_workflow_run_timeline_events.sql` instead. Do not edit an already-applied migration in a shared environment; this project is still local-dev, but prefer one migration per committed schema change.

- [ ] **Step 4: Update WorkflowTaskRow**

Modify `WorkflowTaskRow` to include `configJson`:

```java
public record WorkflowTaskRow(
        String workflowId,
        String taskId,
        String name,
        String type,
        int position,
        String configJson) {

    public WorkflowTaskRow {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        type = (type == null || type.isBlank()) ? TaskDefinition.DEFAULT_TYPE : type;
        configJson = (configJson == null || configJson.isBlank()) ? "{}" : configJson;
    }
}
```

Adjust exact existing constructor fields to match the current file; keep existing fields and add `configJson` last.

- [ ] **Step 5: Add JSON helper in MyBatisWorkflowRepository**

Add an `ObjectMapper` field or static mapper:

```java
private static final ObjectMapper JSON = new ObjectMapper();

private static String toConfigJson(TaskConfig config) {
    try {
        return JSON.writeValueAsString(config.asMap());
    } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Task config must be JSON serializable", e);
    }
}

private static TaskConfig toTaskConfig(String configJson) {
    try {
        @SuppressWarnings("unchecked")
        Map<String, Object> values = JSON.readValue(
                configJson == null || configJson.isBlank() ? "{}" : configJson,
                Map.class);
        return TaskConfig.of(values);
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("Stored task config is not valid JSON", e);
    }
}
```

Imports:

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.domain.dag.TaskConfig;
import java.util.Map;
```

- [ ] **Step 6: Map config in to/from row**

When creating `WorkflowTaskRow` from `TaskDefinition`, set `configJson = toConfigJson(task.config())`.

When creating `TaskDefinition` from row, use:

```java
return new TaskDefinition(
        TaskId.of(row.taskId()),
        row.name(),
        row.type(),
        toTaskConfig(row.configJson()));
```

- [ ] **Step 7: Update mapper XML**

In `WorkflowMapper.xml`:

- Result map constructor adds `config` column.
- Insert includes `config` column.
- Select includes `config` column.

Example select columns:

```sql
select workflow_id, task_id, name, type, position, config
from workflow_tasks
where workflow_id = #{workflowId}
order by position, task_id
```

Example insert:

```xml
insert into workflow_tasks (workflow_id, task_id, name, type, position, config)
values
<foreach collection="tasks" item="task" separator=",">
  (#{task.workflowId}, #{task.taskId}, #{task.name}, #{task.type}, #{task.position}, CAST(#{task.configJson} AS jsonb))
</foreach>
```

- [ ] **Step 8: Run MyBatis tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Run backend compile**

Run:

```bash
mvn -f backend/pom.xml test-compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add backend/graphpilot-adapter-persistence-mybatis \
        backend/graphpilot-domain/src/main/java/com/graphpilot/domain/dag \
        backend/graphpilot-domain/src/test/java/com/graphpilot/domain/dag
git commit -m "feat(persistence): persist workflow task config"
```

---

## Task 3: Expose task config through Spring workflow API

**Files:**
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/CreateWorkflowRequest.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowResponse.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/workflow/WorkflowControllerTest.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/CreateWorkflowApiIntegrationTest.java`

- [ ] **Step 1: Write failing controller test for config round-trip**

In `WorkflowControllerTest`, add/extend create workflow request body to include config:

```java
String body = """
        {
          "name": "ETL",
          "tasks": [
            {
              "id": "extract",
              "name": "Extract data",
              "type": "shell",
              "config": { "command": "echo hi", "timeout": 10 }
            }
          ],
          "edges": []
        }
        """;
```

Assert in get/list response:

```java
.andExpect(jsonPath("$.tasks[0].type").value("shell"))
.andExpect(jsonPath("$.tasks[0].config.command").value("echo hi"))
.andExpect(jsonPath("$.tasks[0].config.timeout").value(10));
```

- [ ] **Step 2: Run Spring web tests and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test -Dtest=WorkflowControllerTest
```

Expected: FAIL because DTOs do not expose config.

- [ ] **Step 3: Update CreateWorkflowRequest**

Task request record should be:

```java
public record TaskRequest(
        String id,
        String name,
        String type,
        Map<String, Object> config) {

    TaskDefinition toTaskDefinition() {
        return new TaskDefinition(
                TaskId.of(id),
                name,
                type,
                config == null ? TaskConfig.empty() : TaskConfig.of(config));
    }
}
```

Imports:

```java
import com.graphpilot.domain.dag.TaskConfig;
import java.util.Map;
```

Keep existing validation behavior: domain constructors validate blank names and ids.

- [ ] **Step 4: Update WorkflowResponse**

Task response should include config:

```java
public record TaskResponse(
        String id,
        String name,
        String type,
        Map<String, Object> config) {

    static TaskResponse from(TaskDefinition task) {
        return new TaskResponse(
                task.id().value(),
                task.name(),
                task.type(),
                task.config().asMap());
    }
}
```

- [ ] **Step 5: Run web tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Run Spring bootstrap integration test**

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=CreateWorkflowApiIntegrationTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-adapter-web-spring backend/graphpilot-bootstrap-spring
git commit -m "feat(api): expose task config in Spring workflow API"
```

---

## Task 4: Pass task config to worker handlers and make handlers deterministic-config aware

**Files:**
- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java`
- Modify: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorServiceTest.java`
- Modify: `backend/graphpilot-adapter-worker/src/main/java/com/graphpilot/adapter/worker/handler/MockTaskHandler.java`
- Modify: `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/MockTaskHandlerTest.java`
- Modify: `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/ShellTaskHandlerTest.java`
- Modify: `backend/graphpilot-adapter-worker/src/test/java/com/graphpilot/adapter/worker/handler/HttpTaskHandlerTest.java`

- [ ] **Step 1: Write failing coordinator test that handler receives config**

In `WorkflowExecutionCoordinatorServiceTest`, add to `ConfigurableTaskHandler`:

```java
private final List<Map<String, Object>> receivedInputs = new ArrayList<>();

@Override
public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
    invocations.add(taskRun.taskId().value());
    invocationOrder.add(taskRun.taskId().value());
    receivedInputs.add(Map.copyOf(input));
    return Objects.requireNonNull(behavior.apply(taskRun), "behavior must not return null");
}
```

Add test:

```java
@Test
void executePassesTaskConfigToHandler() {
    WorkflowRunId runId = WorkflowRunId.of("run-1");
    WorkflowId workflowId = WorkflowId.of("workflow-1");
    workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
    workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
    givenWorkflow(workflowId, new DagDefinition(
            List.of(new TaskDefinition(
                    TaskId.of("task-1"),
                    "Task task-1",
                    "mock",
                    TaskConfig.of(Map.of("success", true, "delayMs", 0)))),
            List.of()));

    coordinator.execute(runId);

    assertThat(taskHandler.receivedInputs).containsExactly(Map.of("success", true, "delayMs", 0));
}
```

Imports:

```java
import com.graphpilot.domain.dag.TaskConfig;
```

- [ ] **Step 2: Run application test and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=WorkflowExecutionCoordinatorServiceTest
```

Expected: FAIL because coordinator passes `Map.of()`.

- [ ] **Step 3: Pass task config in coordinator**

Change:

```java
result = handler.execute(taskRun, taskDef, Map.of());
```

to:

```java
result = handler.execute(taskRun, taskDef, taskDef.config().asMap());
```

- [ ] **Step 4: Update MockTaskHandler**

Replace fixed delay/random success handling with config-aware logic:

```java
@Override
public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
    try {
        long delayMs = getLong(input, "delayMs", DEFAULT_DELAY.toMillis());
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        Object success = input.get("success");
        if (success instanceof Boolean fixedSuccess) {
            return fixedSuccess
                    ? TaskResult.success("Mock task completed successfully")
                    : TaskResult.failure("MOCK_ERROR", "Mock task failed by config");
        }
        return RANDOM.nextDouble() < 0.95
                ? TaskResult.success("Mock task completed successfully")
                : TaskResult.failure("MOCK_ERROR", "Mock task failed randomly");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return TaskResult.failure("Interrupted", e.getMessage());
    }
}

private long getLong(Map<String, Object> input, String key, long defaultValue) {
    Object value = input.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(value.toString());
}
```

- [ ] **Step 5: Update handler tests**

Add to `MockTaskHandlerTest`:

```java
@Test
void executeCanBeForcedToSucceedByConfig() {
    TaskResult result = handler.execute(
            createTaskRun(TaskRunStatus.PENDING),
            createTaskDefinition(),
            Map.of("success", true, "delayMs", 0));

    assertThat(result.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
}

@Test
void executeCanBeForcedToFailByConfig() {
    TaskResult result = handler.execute(
            createTaskRun(TaskRunStatus.PENDING),
            createTaskDefinition(),
            Map.of("success", false, "delayMs", 0));

    assertThat(result.status()).isEqualTo(TaskRunStatus.FAILED);
}
```

Keep Shell/Http tests as-is; they already pass explicit input maps. They now represent task config maps.

- [ ] **Step 6: Run application and worker tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application,graphpilot-adapter-worker -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-application backend/graphpilot-adapter-worker
git commit -m "feat(worker): pass task config to handlers"
```

---

## Task 5: Add pending-run scanner use case

**Files:**
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/ScanPendingWorkflowRunsUseCase.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/ScanResult.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/ScanFailure.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/ScanPendingWorkflowRunsService.java`
- Test: `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/service/ScanPendingWorkflowRunsServiceTest.java`

- [ ] **Step 1: Write failing scanner tests**

Create `ScanPendingWorkflowRunsServiceTest.java`:

```java
package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.ScanFailure;
import com.graphpilot.application.execution.ScanResult;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanPendingWorkflowRunsServiceTest {

    @Test
    void rejectsNonPositiveLimit() {
        var service = new ScanPendingWorkflowRunsService(new FakeRunRepository(), id -> { });

        assertThatThrownBy(() -> service.scan(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pending workflow run scan limit must be positive");
    }

    @Test
    void scansPendingRunsAndExecutesEach() {
        FakeRunRepository repository = new FakeRunRepository();
        WorkflowRun run1 = run("run-1", WorkflowRunStatus.PENDING);
        WorkflowRun run2 = run("run-2", WorkflowRunStatus.PENDING);
        repository.runs.add(run1);
        repository.runs.add(run2);
        RecordingExecutor executor = new RecordingExecutor();
        var service = new ScanPendingWorkflowRunsService(repository, executor);

        ScanResult result = service.scan(10);

        assertThat(repository.lastStatus).isEqualTo(WorkflowRunStatus.PENDING);
        assertThat(repository.lastLimit).isEqualTo(10);
        assertThat(executor.executed).containsExactly(run1.id(), run2.id());
        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.executedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void isolatesExecutionFailures() {
        FakeRunRepository repository = new FakeRunRepository();
        WorkflowRun run1 = run("run-1", WorkflowRunStatus.PENDING);
        WorkflowRun run2 = run("run-2", WorkflowRunStatus.PENDING);
        repository.runs.add(run1);
        repository.runs.add(run2);
        RecordingExecutor executor = new RecordingExecutor();
        executor.failures.add(run1.id());
        var service = new ScanPendingWorkflowRunsService(repository, executor);

        ScanResult result = service.scan(10);

        assertThat(executor.executed).containsExactly(run1.id(), run2.id());
        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.executedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(ScanFailure::workflowRunId)
                .containsExactly(run1.id());
    }

    private static WorkflowRun run(String id, WorkflowRunStatus status) {
        return WorkflowRun.restore(
                WorkflowRunId.of(id),
                WorkflowId.of("workflow-1"),
                status,
                Instant.parse("2026-06-17T00:00:00Z"));
    }

    private static final class RecordingExecutor implements ExecuteWorkflowRunUseCase {
        final List<WorkflowRunId> executed = new ArrayList<>();
        final List<WorkflowRunId> failures = new ArrayList<>();

        @Override
        public void execute(WorkflowRunId workflowRunId) {
            executed.add(workflowRunId);
            if (failures.contains(workflowRunId)) {
                throw new IllegalStateException("boom " + workflowRunId.value());
            }
        }
    }

    private static final class FakeRunRepository implements WorkflowRunRepository {
        final List<WorkflowRun> runs = new ArrayList<>();
        WorkflowRunStatus lastStatus;
        int lastLimit;

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) { return Optional.empty(); }
        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) { return List.of(); }
        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) { return List.of(); }
        @Override
        public List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
            lastStatus = status;
            lastLimit = limit;
            return runs.stream().limit(limit).toList();
        }
    }
}
```

- [ ] **Step 2: Run scanner tests and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=ScanPendingWorkflowRunsServiceTest
```

Expected: compilation fails because scanner types do not exist.

- [ ] **Step 3: Add scanner records and use case**

Create `ScanFailure.java`:

```java
package com.graphpilot.application.execution;

import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.Objects;

public record ScanFailure(WorkflowRunId workflowRunId, String message) {
    public ScanFailure {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        message = (message == null || message.isBlank()) ? "Workflow run execution failed" : message.trim();
    }
}
```

Create `ScanResult.java`:

```java
package com.graphpilot.application.execution;

import java.util.List;

public record ScanResult(
        int scannedCount,
        int executedCount,
        int failedCount,
        List<ScanFailure> failures) {

    public ScanResult {
        failures = List.copyOf(failures);
    }
}
```

Create `ScanPendingWorkflowRunsUseCase.java`:

```java
package com.graphpilot.application.execution.port.in;

import com.graphpilot.application.execution.ScanResult;

public interface ScanPendingWorkflowRunsUseCase {
    ScanResult scan(int limit);
}
```

- [ ] **Step 4: Implement scanner service**

Create `ScanPendingWorkflowRunsService.java`:

```java
package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.ScanFailure;
import com.graphpilot.application.execution.ScanResult;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScanPendingWorkflowRunsService implements ScanPendingWorkflowRunsUseCase {

    private final WorkflowRunRepository workflowRunRepository;
    private final ExecuteWorkflowRunUseCase executeWorkflowRunUseCase;

    public ScanPendingWorkflowRunsService(
            WorkflowRunRepository workflowRunRepository,
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository, "workflowRunRepository must not be null");
        this.executeWorkflowRunUseCase = Objects.requireNonNull(
                executeWorkflowRunUseCase, "executeWorkflowRunUseCase must not be null");
    }

    @Override
    public ScanResult scan(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Pending workflow run scan limit must be positive");
        }
        List<WorkflowRun> pendingRuns = workflowRunRepository.findByStatus(WorkflowRunStatus.PENDING, limit);
        List<ScanFailure> failures = new ArrayList<>();
        int executed = 0;
        for (WorkflowRun run : pendingRuns) {
            try {
                executeWorkflowRunUseCase.execute(run.id());
                executed++;
            } catch (RuntimeException e) {
                failures.add(new ScanFailure(run.id(), e.getMessage()));
            }
        }
        return new ScanResult(pendingRuns.size(), executed, failures.size(), failures);
    }
}
```

- [ ] **Step 5: Run application tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/graphpilot-application
git commit -m "feat(worker): add pending run scanner use case"
```

---

## Task 6: Implement MyBatis findByStatus and scanner bootstrap wrappers

**Files:**
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.xml`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunRepository.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerAssemblyConfiguration.java`
- Create: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerScannerScheduler.java`
- Modify: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/GraphPilotFactory.java`
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkerScannerScheduler.java`
- Test: relevant MyBatis and bootstrap tests.

- [ ] **Step 1: Write failing MyBatis findByStatus test**

Add to `MyBatisWorkflowRunRepositoryIntegrationTest`:

```java
@Test
void findsWorkflowRunsByStatusWithLimit() {
    WorkflowRun pendingA = workflowRun("run-a", "workflow-1", WorkflowRunStatus.PENDING, TRIGGERED_AT);
    WorkflowRun running = workflowRun("run-b", "workflow-1", WorkflowRunStatus.RUNNING, TRIGGERED_AT.plusSeconds(1));
    WorkflowRun pendingC = workflowRun("run-c", "workflow-2", WorkflowRunStatus.PENDING, TRIGGERED_AT.plusSeconds(2));
    repository.save(pendingA, List.of());
    repository.save(running, List.of());
    repository.save(pendingC, List.of());

    assertThat(repository.findByStatus(WorkflowRunStatus.PENDING, 1))
            .extracting(WorkflowRun::id)
            .containsExactly(pendingA.id());
    assertThat(repository.findByStatus(WorkflowRunStatus.PENDING, 10))
            .extracting(WorkflowRun::id)
            .containsExactly(pendingA.id(), pendingC.id());
}
```

- [ ] **Step 2: Run MyBatis test and verify fail**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRunRepositoryIntegrationTest
```

Expected: FAIL because MyBatis repository currently has placeholder `findByStatus`.

- [ ] **Step 3: Add mapper query**

`WorkflowRunMapper.java`:

```java
List<WorkflowRunRow> findWorkflowRunsByStatus(
        @Param("status") String status,
        @Param("limit") int limit);
```

`WorkflowRunMapper.xml`:

```xml
<select id="findWorkflowRunsByStatus" resultMap="WorkflowRunRowResultMap">
    select id, workflow_id, status, triggered_at, started_at, finished_at
    from workflow_runs
    where status = #{status}
    order by triggered_at, id
    limit #{limit}
</select>
```

- [ ] **Step 4: Implement MyBatisWorkflowRunRepository.findByStatus**

Replace placeholder with:

```java
@Override
@Transactional(readOnly = true)
public List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
    Objects.requireNonNull(status, "status must not be null");
    if (limit <= 0) {
        throw new IllegalArgumentException("Workflow run query limit must be positive");
    }
    return workflowRunMapper.findWorkflowRunsByStatus(status.name(), limit).stream()
            .map(MyBatisWorkflowRunRepository::toWorkflowRun)
            .toList();
}
```

- [ ] **Step 5: Add scanner beans in Spring bootstrap**

In `WorkerAssemblyConfiguration`, add bean:

```java
@Bean
ScanPendingWorkflowRunsUseCase scanPendingWorkflowRunsUseCase(
        WorkflowRunRepository workflowRunRepository,
        ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
    return new ScanPendingWorkflowRunsService(workflowRunRepository, executeWorkflowRunUseCase);
}
```

Create `WorkerScannerScheduler.java`:

```java
package com.graphpilot.bootstrap.spring;

import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class WorkerScannerScheduler {

    private final ScanPendingWorkflowRunsUseCase scanner;
    private final boolean enabled;
    private final int limit;

    WorkerScannerScheduler(
            ScanPendingWorkflowRunsUseCase scanner,
            @Value("${graphpilot.worker.scanner.enabled:true}") boolean enabled,
            @Value("${graphpilot.worker.scanner.limit:20}") int limit) {
        this.scanner = scanner;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${graphpilot.worker.scanner.interval-ms:10000}")
    void scan() {
        if (enabled) {
            scanner.scan(limit);
        }
    }
}
```

In `WorkerAssemblyConfiguration`, add `@EnableScheduling` next to `@EnableAsync`.

- [ ] **Step 6: Add scanner beans in Micronaut bootstrap**

In `GraphPilotFactory`, add:

```java
@Singleton
ScanPendingWorkflowRunsUseCase scanPendingWorkflowRunsUseCase(
        WorkflowRunRepository workflowRunRepository,
        ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
    return new ScanPendingWorkflowRunsService(workflowRunRepository, executeWorkflowRunUseCase);
}
```

Create `WorkerScannerScheduler.java`:

```java
package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
final class WorkerScannerScheduler {

    private final ScanPendingWorkflowRunsUseCase scanner;
    private final boolean enabled;
    private final int limit;

    WorkerScannerScheduler(
            ScanPendingWorkflowRunsUseCase scanner,
            @Value("${graphpilot.worker.scanner.enabled:true}") boolean enabled,
            @Value("${graphpilot.worker.scanner.limit:20}") int limit) {
        this.scanner = scanner;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelay = "${graphpilot.worker.scanner.interval:10s}")
    void scan() {
        if (enabled) {
            scanner.scan(limit);
        }
    }
}
```

- [ ] **Step 7: Run tests**

```bash
mvn -f backend/pom.xml test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/graphpilot-adapter-persistence-mybatis backend/graphpilot-bootstrap-spring backend/graphpilot-bootstrap-micronaut
git commit -m "feat(worker): scan pending workflow runs"
```

---

## Task 7: Add timeline domain and application ports

**Files:**
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TimelineEventId.java`
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TimelineEventType.java`
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunTimelineEvent.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/TimelineEventIdGeneratorPort.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/WorkflowRunTimelineRepository.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TimelineRecorder.java`
- Tests in domain/application.

- [ ] **Step 1: Write failing domain tests**

Create `WorkflowRunTimelineEventTest.java`:

```java
package com.graphpilot.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkflowRunTimelineEventTest {

    @Test
    void createsRunLevelEvent() {
        WorkflowRunTimelineEvent event = WorkflowRunTimelineEvent.runLevel(
                TimelineEventId.of("event-1"),
                WorkflowRunId.of("run-1"),
                TimelineEventType.RUN_CREATED,
                "Workflow run created",
                Instant.parse("2026-06-17T00:00:00Z"));

        assertThat(event.taskRunId()).isNull();
        assertThat(event.taskId()).isNull();
        assertThat(event.message()).isEqualTo("Workflow run created");
    }

    @Test
    void createsTaskLevelEvent() {
        WorkflowRunTimelineEvent event = WorkflowRunTimelineEvent.taskLevel(
                TimelineEventId.of("event-1"),
                WorkflowRunId.of("run-1"),
                TaskRunId.of("task-run-1"),
                TaskId.of("extract"),
                TimelineEventType.TASK_SUCCEEDED,
                "Task extract succeeded",
                Instant.parse("2026-06-17T00:00:00Z"));

        assertThat(event.taskRunId()).isEqualTo(TaskRunId.of("task-run-1"));
        assertThat(event.taskId()).isEqualTo(TaskId.of("extract"));
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> WorkflowRunTimelineEvent.runLevel(
                TimelineEventId.of("event-1"),
                WorkflowRunId.of("run-1"),
                TimelineEventType.RUN_CREATED,
                " ",
                Instant.parse("2026-06-17T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Timeline event message must not be blank");
    }
}
```

- [ ] **Step 2: Implement timeline domain types**

`TimelineEventId.java`:

```java
package com.graphpilot.domain.execution;

import java.util.Objects;

public record TimelineEventId(String value) {
    public TimelineEventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Timeline event id must not be blank");
        }
        value = value.trim();
    }

    public static TimelineEventId of(String value) {
        return new TimelineEventId(value);
    }
}
```

`TimelineEventType.java`:

```java
package com.graphpilot.domain.execution;

public enum TimelineEventType {
    RUN_CREATED,
    RUN_STARTED,
    TASK_STARTED,
    TASK_SUCCEEDED,
    TASK_FAILED,
    TASK_SKIPPED,
    RUN_SUCCEEDED,
    RUN_FAILED
}
```

`WorkflowRunTimelineEvent.java`:

```java
package com.graphpilot.domain.execution;

import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import java.util.Objects;

public record WorkflowRunTimelineEvent(
        TimelineEventId id,
        WorkflowRunId workflowRunId,
        TaskRunId taskRunId,
        TaskId taskId,
        TimelineEventType type,
        String message,
        Instant occurredAt) {

    public WorkflowRunTimelineEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Timeline event message must not be blank");
        }
        message = message.trim();
    }

    public static WorkflowRunTimelineEvent runLevel(
            TimelineEventId id,
            WorkflowRunId workflowRunId,
            TimelineEventType type,
            String message,
            Instant occurredAt) {
        return new WorkflowRunTimelineEvent(id, workflowRunId, null, null, type, message, occurredAt);
    }

    public static WorkflowRunTimelineEvent taskLevel(
            TimelineEventId id,
            WorkflowRunId workflowRunId,
            TaskRunId taskRunId,
            TaskId taskId,
            TimelineEventType type,
            String message,
            Instant occurredAt) {
        Objects.requireNonNull(taskRunId, "taskRunId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        return new WorkflowRunTimelineEvent(id, workflowRunId, taskRunId, taskId, type, message, occurredAt);
    }
}
```

- [ ] **Step 3: Add application ports and recorder**

`TimelineEventIdGeneratorPort.java`:

```java
package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.TimelineEventId;

public interface TimelineEventIdGeneratorPort {
    TimelineEventId nextTimelineEventId();
}
```

`WorkflowRunTimelineRepository.java`:

```java
package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.List;

public interface WorkflowRunTimelineRepository {
    WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event);
    List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit);
}
```

`TimelineRecorder.java`:

```java
package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.Objects;

public final class TimelineRecorder {

    private final WorkflowRunTimelineRepository repository;
    private final TimelineEventIdGeneratorPort idGenerator;
    private final ClockPort clock;

    public TimelineRecorder(
            WorkflowRunTimelineRepository repository,
            TimelineEventIdGeneratorPort idGenerator,
            ClockPort clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void run(WorkflowRunId runId, TimelineEventType type, String message) {
        repository.save(WorkflowRunTimelineEvent.runLevel(
                idGenerator.nextTimelineEventId(), runId, type, message, clock.now()));
    }

    public void task(WorkflowRunId runId, TaskRunId taskRunId, TaskId taskId,
            TimelineEventType type, String message) {
        repository.save(WorkflowRunTimelineEvent.taskLevel(
                idGenerator.nextTimelineEventId(), runId, taskRunId, taskId, type, message, clock.now()));
    }
}
```

- [ ] **Step 4: Run domain/application tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-domain,graphpilot-application -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-domain backend/graphpilot-application
git commit -m "feat(execution): add workflow run timeline model"
```

---

## Task 8: Record timeline events in trigger and coordinator

**Files:**
- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TriggerWorkflowRunService.java`
- Modify: `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java`
- Modify tests for both services.

- [ ] **Step 1: Update constructors with TimelineRecorder**

Add `TimelineRecorder timelineRecorder` to both service constructors. To keep existing tests/builds incremental, add overloads using a no-op recorder only if necessary. Preferred approach: wire real recorder in all factories after Task 9.

A no-op helper can be:

```java
public static TimelineRecorder noopTimelineRecorder(ClockPort clock) {
    return new TimelineRecorder(
            event -> event,
            () -> TimelineEventId.of("noop-" + clock.now().toEpochMilli()),
            clock);
}
```

If this is awkward, update all tests to provide a recording repository and deterministic ID generator.

- [ ] **Step 2: Write failing TriggerWorkflowRunService test**

In `TriggerWorkflowRunServiceTest`, add a recording timeline repository and assert after trigger:

```java
assertThat(timelineRepository.events)
        .extracting(WorkflowRunTimelineEvent::type)
        .containsExactly(TimelineEventType.RUN_CREATED);
```

- [ ] **Step 3: Write failing coordinator timeline tests**

Add to `WorkflowExecutionCoordinatorServiceTest`:

```java
@Test
void executeRecordsTimelineForSuccessfulRun() {
    // arrange one pending task with deterministic success
    // act coordinator.execute(runId)
    assertThat(timelineRepository.events)
            .extracting(WorkflowRunTimelineEvent::type)
            .contains(
                    TimelineEventType.RUN_STARTED,
                    TimelineEventType.TASK_STARTED,
                    TimelineEventType.TASK_SUCCEEDED,
                    TimelineEventType.RUN_SUCCEEDED);
}
```

Add failure/skip assertion in existing failure cascade test:

```java
assertThat(timelineRepository.events)
        .extracting(WorkflowRunTimelineEvent::type)
        .contains(TimelineEventType.TASK_FAILED, TimelineEventType.TASK_SKIPPED, TimelineEventType.RUN_FAILED);
```

- [ ] **Step 4: Implement timeline writes in TriggerWorkflowRunService**

After save and before/after publish:

```java
timelineRecorder.run(savedRun.id(), TimelineEventType.RUN_CREATED, "Workflow run created");
eventPublisher.publish(new WorkflowRunCreatedEvent(savedRun.id(), workflow.id()));
```

- [ ] **Step 5: Implement timeline writes in coordinator**

When marking run running:

```java
timelineRecorder.run(workflowRunId, TimelineEventType.RUN_STARTED, "Workflow run started");
```

Before task running update:

```java
timelineRecorder.task(taskRun.workflowRunId(), taskRun.id(), taskRun.taskId(),
        TimelineEventType.TASK_STARTED, "Task " + taskRun.taskId().value() + " started");
```

After terminal task update:

```java
TimelineEventType type = switch (newStatus) {
    case SUCCEEDED -> TimelineEventType.TASK_SUCCEEDED;
    case FAILED -> TimelineEventType.TASK_FAILED;
    case SKIPPED -> TimelineEventType.TASK_SKIPPED;
    case PENDING, RUNNING -> throw new IllegalStateException("Task is not terminal: " + newStatus);
};
timelineRecorder.task(taskRun.workflowRunId(), taskRun.id(), taskRun.taskId(),
        type, "Task " + taskRun.taskId().value() + " " + newStatus.name().toLowerCase());
```

When cascading skipped:

```java
timelineRecorder.task(workflowRunId, skipped.id(), skipped.taskId(),
        TimelineEventType.TASK_SKIPPED,
        "Task " + skipped.taskId().value() + " skipped because dependency failed");
```

When finalizing run:

```java
timelineRecorder.run(workflowRunId,
        finalStatus == WorkflowRunStatus.SUCCEEDED ? TimelineEventType.RUN_SUCCEEDED : TimelineEventType.RUN_FAILED,
        finalStatus == WorkflowRunStatus.SUCCEEDED ? "Workflow run succeeded" : "Workflow run failed");
```

- [ ] **Step 6: Run application tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-application
git commit -m "feat(worker): record structured execution timeline"
```

---

## Task 9: Persist timeline in memory and MyBatis

**Files:**
- Create: `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunTimelineRepository.java`
- Create: `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidTimelineEventIdGenerator.java`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRunTimelineEventRow.java`
- Create/modify MyBatis mapper interfaces/XML.
- Update migration created in Task 2 or add next migration.

- [ ] **Step 1: Write in-memory timeline repository test**

Create `InMemoryWorkflowRunTimelineRepositoryTest.java`:

```java
@Test
void savesAndFindsEventsSortedByOccurredAtThenIdWithLimit() {
    var repository = new InMemoryWorkflowRunTimelineRepository();
    WorkflowRunId runId = WorkflowRunId.of("run-1");
    WorkflowRunTimelineEvent second = WorkflowRunTimelineEvent.runLevel(
            TimelineEventId.of("event-b"), runId, TimelineEventType.RUN_STARTED,
            "started", Instant.parse("2026-06-17T00:00:01Z"));
    WorkflowRunTimelineEvent first = WorkflowRunTimelineEvent.runLevel(
            TimelineEventId.of("event-a"), runId, TimelineEventType.RUN_CREATED,
            "created", Instant.parse("2026-06-17T00:00:00Z"));

    repository.save(second);
    repository.save(first);

    assertThat(repository.findByWorkflowRunId(runId, 10)).containsExactly(first, second);
    assertThat(repository.findByWorkflowRunId(runId, 1)).containsExactly(first);
}
```

- [ ] **Step 2: Implement in-memory repository and ID generator**

`InMemoryWorkflowRunTimelineRepository.java`:

```java
public final class InMemoryWorkflowRunTimelineRepository implements WorkflowRunTimelineRepository {
    private final ConcurrentMap<WorkflowRunId, List<WorkflowRunTimelineEvent>> events = new ConcurrentHashMap<>();

    @Override
    public WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.compute(event.workflowRunId(), (runId, existing) -> {
            List<WorkflowRunTimelineEvent> copy = new ArrayList<>(existing == null ? List.of() : existing);
            copy.add(event);
            return List.copyOf(copy);
        });
        return event;
    }

    @Override
    public List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Timeline event query limit must be positive");
        return events.getOrDefault(workflowRunId, List.of()).stream()
                .sorted(Comparator.comparing(WorkflowRunTimelineEvent::occurredAt)
                        .thenComparing(event -> event.id().value()))
                .limit(limit)
                .toList();
    }
}
```

`UuidTimelineEventIdGenerator.java`:

```java
public final class UuidTimelineEventIdGenerator implements TimelineEventIdGeneratorPort {
    @Override
    public TimelineEventId nextTimelineEventId() {
        return TimelineEventId.of(UUID.randomUUID().toString());
    }
}
```

- [ ] **Step 3: Add MyBatis migration**

If V5 already contains only config column and is committed, create `V6__create_workflow_run_timeline_events.sql`:

```sql
create table workflow_run_timeline_events (
  id text primary key,
  workflow_run_id text not null references workflow_runs(id) on delete cascade,
  task_run_id text,
  task_id text,
  type text not null,
  message text not null,
  occurred_at timestamptz not null
);

create index idx_workflow_run_timeline_events_run_time
  on workflow_run_timeline_events(workflow_run_id, occurred_at, id);
```

If V5 is uncommitted at this point, append the same table to V5 before committing.

- [ ] **Step 4: Add MyBatis row and mapper**

`WorkflowRunTimelineEventRow.java`:

```java
public record WorkflowRunTimelineEventRow(
        String id,
        String workflowRunId,
        String taskRunId,
        String taskId,
        String type,
        String message,
        Instant occurredAt) { }
```

Create mapper interface `WorkflowRunTimelineMapper.java`:

```java
@Mapper
public interface WorkflowRunTimelineMapper {
    void insert(WorkflowRunTimelineEventRow row);
    List<WorkflowRunTimelineEventRow> findByWorkflowRunId(
            @Param("workflowRunId") String workflowRunId,
            @Param("limit") int limit);
}
```

Create XML `WorkflowRunTimelineMapper.xml` with insert/select ordered by `occurred_at, id`.

- [ ] **Step 5: Implement MyBatisWorkflowRunTimelineRepository**

Map row to domain and domain to row. Use nullable `taskRunId/taskId` for run-level events.

- [ ] **Step 6: Run persistence tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-memory,graphpilot-adapter-persistence-mybatis -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-adapter-persistence-memory backend/graphpilot-adapter-persistence-mybatis
git commit -m "feat(persistence): persist workflow run timeline events"
```

---

## Task 10: Wire timeline/scanner in Spring and expose timeline API

**Files:**
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowRunAssemblyConfiguration.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerAssemblyConfiguration.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunController.java`
- Create: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/TimelineEventResponse.java`
- Tests.

- [ ] **Step 1: Add query use case for timeline or use repository in controller via application service**

Preferred: add method to `QueryWorkflowRunUseCase`:

```java
List<WorkflowRunTimelineEvent> findTimelineByRunId(WorkflowRunId workflowRunId, int limit);
```

Update `QueryWorkflowRunService` constructor with `WorkflowRunTimelineRepository` and implement:

```java
@Override
public List<WorkflowRunTimelineEvent> findTimelineByRunId(WorkflowRunId workflowRunId, int limit) {
    if (limit <= 0 || limit > 500) {
        throw new IllegalArgumentException("Timeline event query limit must be between 1 and 500");
    }
    return timelineRepository.findByWorkflowRunId(workflowRunId, limit);
}
```

Update tests accordingly.

- [ ] **Step 2: Add TimelineEventResponse**

```java
public record TimelineEventResponse(
        String id,
        String workflowRunId,
        String taskRunId,
        String taskId,
        String type,
        String message,
        Instant occurredAt) {

    static TimelineEventResponse from(WorkflowRunTimelineEvent event) {
        return new TimelineEventResponse(
                event.id().value(),
                event.workflowRunId().value(),
                event.taskRunId() == null ? null : event.taskRunId().value(),
                event.taskId() == null ? null : event.taskId().value(),
                event.type().name(),
                event.message(),
                event.occurredAt());
    }
}
```

- [ ] **Step 3: Add controller endpoint**

In `WorkflowRunController`:

```java
@GetMapping("/workflow-runs/{runId}/timeline")
ResponseEntity<List<TimelineEventResponse>> listTimeline(
        @PathVariable("runId") String runId,
        @RequestParam(name = "limit", defaultValue = "200") int limit) {
    int boundedLimit = validateTimelineLimit(limit);
    return ResponseEntity.ok(queryWorkflowRunUseCase
            .findTimelineByRunId(workflowRunIdFrom(runId), boundedLimit)
            .stream()
            .map(TimelineEventResponse::from)
            .toList());
}

private static int validateTimelineLimit(int limit) {
    if (limit <= 0 || limit > 500) {
        throw new IllegalArgumentException("Timeline event query limit must be between 1 and 500");
    }
    return limit;
}
```

- [ ] **Step 4: Wire Spring beans**

In `WorkflowRunAssemblyConfiguration`, add memory timeline repository and ID generator beans for non-postgres; add MyBatis timeline repository bean in postgres profile if config file owns persistence assembly.

Wire `TriggerWorkflowRunService`, `QueryWorkflowRunService`, and `WorkflowExecutionCoordinatorService` with `TimelineRecorder`.

- [ ] **Step 5: Run Spring web/bootstrap tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring,graphpilot-bootstrap-spring -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/graphpilot-application backend/graphpilot-adapter-web-spring backend/graphpilot-bootstrap-spring
git commit -m "feat(api): expose workflow run timeline in Spring"
```

---

## Task 11: Align Micronaut API with Spring

**Files:**
- Modify: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkflowRunController.java`
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/WorkflowController.java`
- Create: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/MicronautErrorHandler.java`
- Modify: `backend/graphpilot-bootstrap-micronaut/src/main/java/com/graphpilot/bootstrap/micronaut/GraphPilotFactory.java`
- Modify tests.

- [ ] **Step 1: Add Micronaut workflow controller with Spring-compatible routes**

Create `WorkflowController.java` with:

```java
@Controller("/api/workflows")
final class WorkflowController {
    // inject CreateWorkflowUseCase, QueryWorkflowUseCase, ChangeWorkflowLifecycleUseCase

    @Post
    HttpResponse<CreateWorkflowResponse> create(@Body CreateWorkflowRequest request) { ... }

    @Get
    List<WorkflowResponse> list(@QueryValue(defaultValue = "50") int limit) { ... }

    @Get("/{id}")
    WorkflowResponse get(@PathVariable String id) { ... }

    @Post("/{id}/activate")
    WorkflowResponse activate(@PathVariable String id) { ... }

    @Post("/{id}/pause")
    WorkflowResponse pause(@PathVariable String id) { ... }

    @Post("/{id}/resume")
    WorkflowResponse resume(@PathVariable String id) { ... }

    @Post("/{id}/archive")
    WorkflowResponse archive(@PathVariable String id) { ... }
}
```

Use records matching Spring DTO fields, including task `config`.

- [ ] **Step 2: Update Micronaut run controller routes**

Ensure routes match Spring:

```java
@Post("/api/workflows/{workflowId}/runs")
@Get("/api/workflows/{workflowId}/runs")
@Get("/api/workflow-runs/{runId}")
@Get("/api/workflow-runs/{runId}/tasks")
@Get("/api/workflow-runs/{runId}/timeline")
```

Return DTOs matching Spring fields.

- [ ] **Step 3: Add Micronaut error handler**

Create `MicronautErrorHandler.java` with exception handlers:

```java
@Produces
@Singleton
final class MicronautErrorHandler implements ExceptionHandler<RuntimeException, HttpResponse<ErrorResponse>> {
    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, RuntimeException exception) {
        if (exception instanceof WorkflowNotFoundException || exception instanceof WorkflowRunNotFoundException) {
            return HttpResponse.notFound(new ErrorResponse("Resource not found", exception.getMessage()));
        }
        if (exception instanceof WorkflowLifecycleException || exception instanceof WorkflowRunTriggerException) {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Conflict", exception.getMessage()));
        }
        if (exception instanceof IllegalArgumentException) {
            return HttpResponse.badRequest(new ErrorResponse("Invalid request", exception.getMessage()));
        }
        return HttpResponse.serverError(new ErrorResponse("Internal server error", "Unexpected error"));
    }
}

record ErrorResponse(String title, String detail) { }
```

If Micronaut requires one handler per exception type for specificity, create focused classes:

- `WorkflowNotFoundHandler`
- `WorkflowRunNotFoundHandler`
- `IllegalArgumentHandler`
- `ConflictHandler`

- [ ] **Step 4: Write Micronaut parity tests**

Extend `WorkflowRunEndToEndTest` or create `MicronautApiParityTest`:

```java
@Test
void workflowApiMatchesSpringShape() {
    Map<String, Object> request = Map.of(
            "name", "Config ETL",
            "tasks", List.of(Map.of(
                    "id", "extract",
                    "name", "Extract",
                    "type", "poc",
                    "config", Map.of("success", true))),
            "edges", List.of());

    Map<String, Object> created = post("/api/workflows", request);
    String workflowId = (String) created.get("id");

    Map<String, Object> workflow = get("/api/workflows/" + workflowId);
    assertThat(workflow.get("status")).isEqualTo("DRAFT");
    assertThat((List<?>) workflow.get("tasks")).hasSize(1);

    Map<String, Object> active = post("/api/workflows/" + workflowId + "/activate", Map.of());
    assertThat(active.get("status")).isEqualTo("ACTIVE");
}
```

Also assert run/tasks/timeline endpoints in E2E.

- [ ] **Step 5: Run Micronaut tests**

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-micronaut -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/graphpilot-bootstrap-micronaut
git commit -m "feat(micronaut): align runtime API with Spring"
```

---

## Task 12: Add frontend config support and timeline tab

**Files:**
- Modify: `apps/web/src/lib/types/index.ts`
- Modify: `apps/web/src/lib/api/workflows.ts`
- Modify: `apps/web/src/lib/api/workflow-runs.ts`
- Modify: `apps/web/src/hooks/use-workflow-runs.ts`
- Create: `apps/web/src/components/run/workflow-run-timeline.tsx`
- Modify: `apps/web/src/app/(console)/workflow-runs/[id]/page.tsx`
- Modify workflow create UI files under `apps/web/src/app/(console)/workflows/new/page.tsx` or related components.

- [ ] **Step 1: Update frontend types**

In `apps/web/src/lib/types/index.ts`:

```ts
export interface TaskDefinition {
  id: string;
  name: string;
  type?: string;
  config?: Record<string, unknown>;
}

export interface CreateWorkflowRequest {
  name: string;
  tasks: Array<{ id: string; name: string; type?: string; config?: Record<string, unknown> }>;
  edges: Array<{ fromTaskId: string; toTaskId: string }>;
}

export type TimelineEventType =
  | "RUN_CREATED"
  | "RUN_STARTED"
  | "TASK_STARTED"
  | "TASK_SUCCEEDED"
  | "TASK_FAILED"
  | "TASK_SKIPPED"
  | "RUN_SUCCEEDED"
  | "RUN_FAILED";

export interface WorkflowRunTimelineEvent {
  id: string;
  workflowRunId: string;
  taskRunId?: string;
  taskId?: string;
  type: TimelineEventType;
  message: string;
  occurredAt: string;
}
```

- [ ] **Step 2: Add timeline API**

In `apps/web/src/lib/api/workflow-runs.ts`:

```ts
export function listTimelineEvents(runId: string, limit = 200): Promise<WorkflowRunTimelineEvent[]> {
  return request<WorkflowRunTimelineEvent[]>(
    `/api/workflow-runs/${encodeURIComponent(runId)}/timeline${buildQueryString({ limit })}`,
  );
}
```

- [ ] **Step 3: Add timeline hook with polling**

In `apps/web/src/hooks/use-workflow-runs.ts`:

```ts
export function useWorkflowRunTimeline(runId: string, isRunning: boolean, limit = 200) {
  return useQuery({
    queryKey: ["workflow-run-timeline", runId, limit],
    queryFn: () => runsApi.listTimelineEvents(runId, limit),
    enabled: !!runId,
    refetchInterval: isRunning ? POLL_INTERVAL_MS : false,
  });
}
```

- [ ] **Step 4: Create timeline component**

Create `apps/web/src/components/run/workflow-run-timeline.tsx`:

```tsx
"use client";

import type { WorkflowRunTimelineEvent, TimelineEventType } from "@/lib/types";

interface WorkflowRunTimelineProps {
  events: WorkflowRunTimelineEvent[];
  isLoading?: boolean;
  error?: Error | null;
}

const EVENT_STYLES: Record<TimelineEventType, { label: string; color: string }> = {
  RUN_CREATED: { label: "运行创建", color: "bg-slate-400" },
  RUN_STARTED: { label: "运行开始", color: "bg-blue-500" },
  TASK_STARTED: { label: "任务开始", color: "bg-blue-500" },
  TASK_SUCCEEDED: { label: "任务成功", color: "bg-green-500" },
  TASK_FAILED: { label: "任务失败", color: "bg-red-500" },
  TASK_SKIPPED: { label: "任务跳过", color: "bg-zinc-400" },
  RUN_SUCCEEDED: { label: "运行成功", color: "bg-green-500" },
  RUN_FAILED: { label: "运行失败", color: "bg-red-500" },
};

export function WorkflowRunTimeline({ events, isLoading, error }: WorkflowRunTimelineProps) {
  if (isLoading) return <div className="py-4 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-4 text-center text-destructive">加载失败: {error.message}</div>;
  if (!events.length) return <div className="py-4 text-center text-muted-foreground">暂无 timeline 事件</div>;

  return (
    <div className="space-y-4">
      {events.map((event) => {
        const style = EVENT_STYLES[event.type];
        return (
          <div key={event.id} className="flex gap-3">
            <div className="flex flex-col items-center">
              <span className={`mt-1 size-3 rounded-full ${style.color}`} />
              <span className="mt-1 h-full w-px bg-border" />
            </div>
            <div className="min-w-0 flex-1 rounded-lg border bg-card p-3">
              <div className="flex items-center justify-between gap-4">
                <div className="font-medium">{style.label}</div>
                <div className="text-xs text-muted-foreground">
                  {new Date(event.occurredAt).toLocaleString("zh-CN")}
                </div>
              </div>
              <div className="mt-1 text-sm text-muted-foreground">{event.message}</div>
              {event.taskId && (
                <div className="mt-2 font-mono text-xs text-muted-foreground">task: {event.taskId}</div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 5: Add Timeline tab to run detail page**

In `apps/web/src/app/(console)/workflow-runs/[id]/page.tsx`:

```tsx
const { data: timeline = [], isLoading: timelineLoading, error: timelineError } =
  useWorkflowRunTimeline(runId, isRunning);
```

Add tab trigger:

```tsx
<TabsTrigger value="timeline">Timeline</TabsTrigger>
```

Add content:

```tsx
<TabsContent value="timeline" className="mt-4">
  <WorkflowRunTimeline
    events={timeline}
    isLoading={timelineLoading}
    error={timelineError instanceof Error ? timelineError : null}
  />
</TabsContent>
```

- [ ] **Step 6: Add workflow create config JSON field**

Find the current create workflow form. If it is inline in `apps/web/src/app/(console)/workflows/new/page.tsx`, update task rows to include:

- type input/select default `mock`
- config JSON textarea default `{}`

Add a parser helper:

```ts
function parseTaskConfig(value: string): Record<string, unknown> {
  const trimmed = value.trim();
  if (!trimmed) return {};
  const parsed: unknown = JSON.parse(trimmed);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("任务 config 必须是 JSON object");
  }
  return parsed as Record<string, unknown>;
}
```

On submit, convert each task to `{ id, name, type, config }`. Show parse errors with the existing toast/error pattern.

- [ ] **Step 7: Run frontend checks**

```bash
cd apps/web
npx tsc --noEmit
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add apps/web
git commit -m "feat(web): add workflow task config and run timeline UI"
```

---

## Task 13: Full verification and documentation update

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture/overview.md`
- Modify: `docs/architecture/adr/0004-framework-free-worker-core.md` only if runtime/API parity text is stale.

- [ ] **Step 1: Run full backend tests**

```bash
mvn -f backend/pom.xml test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run frontend checks**

```bash
cd apps/web
npx tsc --noEmit
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 3: Update architecture overview**

In `docs/architecture/overview.md`, add notes that:

- `TaskDefinition` now includes static config.
- Worker scanner executes PENDING runs conservatively.
- Timeline endpoint exists.
- Spring and Micronaut runtime APIs are aligned for workflow/run/timeline.

- [ ] **Step 4: Update README module/API summary**

In `README.md`, add short bullets under backend capabilities:

```markdown
- Task definitions support static JSON config used by worker handlers.
- Worker can execute runs from both creation events and conservative PENDING-run scans.
- Run detail APIs expose tasks, output, and structured timeline events.
- Spring Boot and Micronaut bootstraps expose aligned REST APIs for current workflow/run features.
```

- [ ] **Step 5: Final git status**

```bash
git status --short
```

Expected: only README/docs changes are unstaged before commit, or clean after commit.

- [ ] **Step 6: Commit docs**

```bash
git add README.md docs/architecture/overview.md docs/architecture/adr/0004-framework-free-worker-core.md
git commit -m "docs: update worker config scanner timeline architecture"
```

- [ ] **Step 7: Push**

```bash
git push origin main
```

Expected: push succeeds.

---

## Plan Self-Review

### Spec coverage

- Static `TaskDefinition.config`: Tasks 1-4 and 12.
- Worker scanner / conservative recovery: Tasks 5-6.
- Structured timeline: Tasks 7-10 and 12.
- Micronaut API parity: Task 11.
- Frontend timeline/config: Task 12.
- Documentation and final verification: Task 13.

### Placeholder scan

No placeholder markers are intentionally left in this plan. Each task contains concrete file paths, expected tests, and code snippets for the core changes.

### Type consistency

- `TaskConfig`, `WorkflowRunTimelineEvent`, `TimelineEventType`, `TimelineRecorder`, and scanner types are introduced before later tasks use them.
- REST response field names match the approved spec and current frontend naming conventions.
- Scanner uses `WorkflowRunStatus.PENDING` only and does not mutate RUNNING runs.
