# Workflow Lifecycle Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal Workflow lifecycle/status state machine with action endpoints and memory/PostgreSQL persistence.

**Architecture:** Keep lifecycle rules in `graphpilot-domain`, expose them through an application inbound port, map them to Spring Web action endpoints, and persist the status through both existing repository adapters. The default profile remains in-memory; the `postgres` profile remains MyBatis/Flyway-backed.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot 3.3.5, MyBatis XML mapper, Flyway, JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL.

---

## Source Design

Implement the approved spec:

- `docs/superpowers/specs/2026-06-14-workflow-lifecycle-status-design.md`

## Scope and Safety Notes

- Do not implement workflow runs, task runs, scheduler, workers, triggers, audit events, API envelopes, update/delete definition APIs, or archive recovery.
- Do not move lifecycle rules into the controller or persistence layer.
- Keep `graphpilot-domain` framework-free.
- Keep `graphpilot-application` dependent only on `graphpilot-domain`.
- Keep commits focused. If commit authorization is unavailable, stop after verification and ask before committing.
- Docker-dependent Testcontainers tests may skip on machines without Docker; report skipped container tests exactly.

## File Structure

### Create

- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowStatus.java`
  - Domain enum for definition lifecycle states.
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowLifecycleException.java`
  - Domain exception for illegal lifecycle transitions.
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/port/in/ChangeWorkflowLifecycleUseCase.java`
  - Inbound port for lifecycle actions.
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/service/ChangeWorkflowLifecycleService.java`
  - Use case implementation that loads, transitions, and saves workflows.
- `backend/graphpilot-application/src/test/java/com/graphpilot/application/workflow/ChangeWorkflowLifecycleServiceTest.java`
  - Application service tests.

### Modify

- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/Workflow.java`
  - Add `status`, default `DRAFT`, restore factory, and transition methods.
- `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/workflow/WorkflowTest.java`
  - Add lifecycle tests.
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowResponse.java`
  - Add `status` to API response.
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowController.java`
  - Inject lifecycle use case and add action endpoints.
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowHttpExceptionHandler.java`
  - Map `WorkflowLifecycleException` to `409 Conflict`.
- `backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/workflow/WorkflowControllerTest.java`
  - Cover response status and lifecycle endpoints.
- `backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRepositoryTest.java`
  - Cover status preservation.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java`
  - Add `status` row field.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java`
  - Map status to/from row.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`
  - Add `status` to workflow result map and SQL.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql`
  - Add status column with enum-like check constraint.
- `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java`
  - Cover status persistence.
- `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java`
  - Register lifecycle use case bean.
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/GraphPilotApplicationTest.java`
  - Assert lifecycle use case bean loads.
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/CreateWorkflowApiIntegrationTest.java`
  - Assert created workflow is returned as `DRAFT` by get/list.
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java`
  - Activate workflow through HTTP and assert persisted status.

---

## Task 1: Add domain lifecycle model

**Files:**
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowStatus.java`
- Create: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowLifecycleException.java`
- Modify: `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/Workflow.java`
- Modify: `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/workflow/WorkflowTest.java`

- [ ] **Step 1: Add failing domain tests**

Append these tests and helper methods to `WorkflowTest`:

```java
    @Test
    void createsWorkflowInDraftStatusByDefault() {
        Workflow workflow = workflow();

        assertEquals(WorkflowStatus.DRAFT, workflow.status());
    }

    @Test
    void transitionsFromDraftToActive() {
        Workflow workflow = workflow();

        Workflow activatedWorkflow = workflow.activate();

        assertEquals(WorkflowStatus.ACTIVE, activatedWorkflow.status());
        assertEquals(WorkflowStatus.DRAFT, workflow.status());
    }

    @Test
    void transitionsFromActiveToPausedAndBackToActive() {
        Workflow activeWorkflow = workflow().activate();

        Workflow pausedWorkflow = activeWorkflow.pause();
        Workflow resumedWorkflow = pausedWorkflow.resume();

        assertEquals(WorkflowStatus.PAUSED, pausedWorkflow.status());
        assertEquals(WorkflowStatus.ACTIVE, resumedWorkflow.status());
        assertEquals(WorkflowStatus.ACTIVE, activeWorkflow.status());
    }

    @Test
    void archivesActiveAndPausedWorkflows() {
        Workflow archivedFromActive = workflow().activate().archive();
        Workflow archivedFromPaused = workflow().activate().pause().archive();

        assertEquals(WorkflowStatus.ARCHIVED, archivedFromActive.status());
        assertEquals(WorkflowStatus.ARCHIVED, archivedFromPaused.status());
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        Workflow draftWorkflow = workflow();
        Workflow activeWorkflow = draftWorkflow.activate();
        Workflow pausedWorkflow = activeWorkflow.pause();
        Workflow archivedWorkflow = activeWorkflow.archive();

        assertThrows(WorkflowLifecycleException.class, draftWorkflow::pause);
        assertThrows(WorkflowLifecycleException.class, draftWorkflow::resume);
        assertThrows(WorkflowLifecycleException.class, draftWorkflow::archive);
        assertThrows(WorkflowLifecycleException.class, activeWorkflow::activate);
        assertThrows(WorkflowLifecycleException.class, pausedWorkflow::pause);
        assertThrows(WorkflowLifecycleException.class, archivedWorkflow::activate);
        assertThrows(WorkflowLifecycleException.class, archivedWorkflow::pause);
        assertThrows(WorkflowLifecycleException.class, archivedWorkflow::resume);
        assertThrows(WorkflowLifecycleException.class, archivedWorkflow::archive);
    }

    @Test
    void restoresWorkflowWithPersistedStatus() {
        DagDefinition dag = dag();

        Workflow workflow = Workflow.restore(
                WorkflowId.of("workflow-1"),
                WorkflowName.of("Daily ETL"),
                dag,
                WorkflowStatus.PAUSED,
                CREATED_AT);

        assertEquals(WorkflowStatus.PAUSED, workflow.status());
        assertEquals(dag, workflow.dag());
        assertEquals(CREATED_AT, workflow.createdAt());
    }

    @Test
    void rejectsMissingWorkflowStatus() {
        assertThrows(
                NullPointerException.class,
                () -> Workflow.restore(
                        WorkflowId.of("workflow-1"),
                        WorkflowName.of("Daily ETL"),
                        dag(),
                        null,
                        CREATED_AT));
    }

    private static Workflow workflow() {
        return Workflow.create(
                WorkflowId.of("workflow-1"),
                WorkflowName.of("Daily ETL"),
                dag(),
                CREATED_AT);
    }

    private static DagDefinition dag() {
        return new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());
    }
```

Keep the existing tests. Update existing local variables that build the same DAG inline to call `dag()` if desired.

- [ ] **Step 2: Run domain test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=WorkflowTest
```

Expected: compilation fails because `WorkflowStatus`, `WorkflowLifecycleException`, `Workflow.status()`, transition methods, and `Workflow.restore(...)` do not exist.

- [ ] **Step 3: Create `WorkflowStatus`**

Create `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowStatus.java`:

```java
package com.graphpilot.domain.workflow;

public enum WorkflowStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ARCHIVED
}
```

- [ ] **Step 4: Create `WorkflowLifecycleException`**

Create `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowLifecycleException.java`:

```java
package com.graphpilot.domain.workflow;

public class WorkflowLifecycleException extends RuntimeException {

    public WorkflowLifecycleException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Replace `Workflow` with lifecycle-aware implementation**

Replace `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/Workflow.java` with:

```java
package com.graphpilot.domain.workflow;

import com.graphpilot.domain.dag.DagDefinition;
import java.time.Instant;
import java.util.Objects;

public record Workflow(
        WorkflowId id,
        WorkflowName name,
        DagDefinition dag,
        WorkflowStatus status,
        Instant createdAt) {

    public Workflow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dag, "dag must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Workflow create(
            WorkflowId id,
            WorkflowName name,
            DagDefinition dag,
            Instant createdAt) {
        return restore(id, name, dag, WorkflowStatus.DRAFT, createdAt);
    }

    public static Workflow restore(
            WorkflowId id,
            WorkflowName name,
            DagDefinition dag,
            WorkflowStatus status,
            Instant createdAt) {
        return new Workflow(id, name, dag, status, createdAt);
    }

    public Workflow activate() {
        if (status == WorkflowStatus.DRAFT || status == WorkflowStatus.PAUSED) {
            return withStatus(WorkflowStatus.ACTIVE);
        }
        throw invalidTransition("activate");
    }

    public Workflow pause() {
        if (status == WorkflowStatus.ACTIVE) {
            return withStatus(WorkflowStatus.PAUSED);
        }
        throw invalidTransition("pause");
    }

    public Workflow resume() {
        if (status == WorkflowStatus.PAUSED) {
            return withStatus(WorkflowStatus.ACTIVE);
        }
        throw invalidTransition("resume");
    }

    public Workflow archive() {
        if (status == WorkflowStatus.ACTIVE || status == WorkflowStatus.PAUSED) {
            return withStatus(WorkflowStatus.ARCHIVED);
        }
        throw invalidTransition("archive");
    }

    private Workflow withStatus(WorkflowStatus nextStatus) {
        return restore(id, name, dag, nextStatus, createdAt);
    }

    private WorkflowLifecycleException invalidTransition(String action) {
        return new WorkflowLifecycleException("Cannot " + action + " workflow from status " + status);
    }
}
```

- [ ] **Step 6: Run domain tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=WorkflowTest
```

Expected: `BUILD SUCCESS`; `WorkflowTest` passes.

- [ ] **Step 7: Commit domain lifecycle model if authorized**

```bash
git add backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/Workflow.java \
    backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowStatus.java \
    backend/graphpilot-domain/src/main/java/com/graphpilot/domain/workflow/WorkflowLifecycleException.java \
    backend/graphpilot-domain/src/test/java/com/graphpilot/domain/workflow/WorkflowTest.java
git commit -m "feat: add workflow lifecycle domain model"
```

---

## Task 2: Add application lifecycle use case

**Files:**
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/port/in/ChangeWorkflowLifecycleUseCase.java`
- Create: `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/service/ChangeWorkflowLifecycleService.java`
- Create: `backend/graphpilot-application/src/test/java/com/graphpilot/application/workflow/ChangeWorkflowLifecycleServiceTest.java`

- [ ] **Step 1: Write failing application service tests**

Create `backend/graphpilot-application/src/test/java/com/graphpilot/application/workflow/ChangeWorkflowLifecycleServiceTest.java`:

```java
package com.graphpilot.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.ChangeWorkflowLifecycleService;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeWorkflowLifecycleServiceTest {

    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");
    private static final Instant CREATED_AT = Instant.parse("2026-06-14T00:00:00Z");

    private final RecordingWorkflowRepository repository = new RecordingWorkflowRepository();
    private final ChangeWorkflowLifecycleService service = new ChangeWorkflowLifecycleService(repository);

    @Test
    void activateTransitionsDraftWorkflowAndSavesIt() {
        repository.workflow = Optional.of(workflow(WorkflowStatus.DRAFT));

        Workflow result = service.activate(WORKFLOW_ID);

        assertThat(result.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(repository.savedWorkflows).containsExactly(result);
    }

    @Test
    void pauseTransitionsActiveWorkflowAndSavesIt() {
        repository.workflow = Optional.of(workflow(WorkflowStatus.ACTIVE));

        Workflow result = service.pause(WORKFLOW_ID);

        assertThat(result.status()).isEqualTo(WorkflowStatus.PAUSED);
        assertThat(repository.savedWorkflows).containsExactly(result);
    }

    @Test
    void resumeTransitionsPausedWorkflowAndSavesIt() {
        repository.workflow = Optional.of(workflow(WorkflowStatus.PAUSED));

        Workflow result = service.resume(WORKFLOW_ID);

        assertThat(result.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(repository.savedWorkflows).containsExactly(result);
    }

    @Test
    void archiveTransitionsActiveWorkflowAndSavesIt() {
        repository.workflow = Optional.of(workflow(WorkflowStatus.ACTIVE));

        Workflow result = service.archive(WORKFLOW_ID);

        assertThat(result.status()).isEqualTo(WorkflowStatus.ARCHIVED);
        assertThat(repository.savedWorkflows).containsExactly(result);
    }

    @Test
    void throwsWhenWorkflowIsMissing() {
        repository.workflow = Optional.empty();

        assertThatThrownBy(() -> service.activate(WORKFLOW_ID))
                .isInstanceOf(WorkflowNotFoundException.class)
                .hasMessage("Workflow not found: id=workflow-1");
        assertThat(repository.savedWorkflows).isEmpty();
    }

    @Test
    void rejectsNullWorkflowId() {
        assertThatThrownBy(() -> service.activate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workflowId must not be null");
    }

    private static Workflow workflow(WorkflowStatus status) {
        return Workflow.restore(
                WORKFLOW_ID,
                WorkflowName.of("Daily ETL"),
                new DagDefinition(
                        List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                        List.of()),
                status,
                CREATED_AT);
    }

    private static final class RecordingWorkflowRepository implements WorkflowRepository {

        private Optional<Workflow> workflow = Optional.empty();
        private final List<Workflow> savedWorkflows = new ArrayList<>();

        @Override
        public Workflow save(Workflow workflow) {
            savedWorkflows.add(workflow);
            this.workflow = Optional.of(workflow);
            return workflow;
        }

        @Override
        public Optional<Workflow> findById(WorkflowId workflowId) {
            return workflow;
        }

        @Override
        public List<Workflow> findAll(int limit) {
            return workflow.stream().limit(limit).toList();
        }
    }
}
```

- [ ] **Step 2: Run application test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=ChangeWorkflowLifecycleServiceTest
```

Expected: compilation fails because `ChangeWorkflowLifecycleService` does not exist.

- [ ] **Step 3: Create lifecycle inbound port**

Create `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/port/in/ChangeWorkflowLifecycleUseCase.java`:

```java
package com.graphpilot.application.workflow.port.in;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;

public interface ChangeWorkflowLifecycleUseCase {

    Workflow activate(WorkflowId workflowId);

    Workflow pause(WorkflowId workflowId);

    Workflow resume(WorkflowId workflowId);

    Workflow archive(WorkflowId workflowId);
}
```

- [ ] **Step 4: Create lifecycle service**

Create `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/service/ChangeWorkflowLifecycleService.java`:

```java
package com.graphpilot.application.workflow.service;

import com.graphpilot.application.workflow.WorkflowNotFoundException;
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class ChangeWorkflowLifecycleService implements ChangeWorkflowLifecycleUseCase {

    private final WorkflowRepository workflowRepository;

    public ChangeWorkflowLifecycleService(WorkflowRepository workflowRepository) {
        this.workflowRepository = Objects.requireNonNull(
                workflowRepository,
                "workflowRepository must not be null");
    }

    @Override
    public Workflow activate(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::activate);
    }

    @Override
    public Workflow pause(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::pause);
    }

    @Override
    public Workflow resume(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::resume);
    }

    @Override
    public Workflow archive(WorkflowId workflowId) {
        return changeStatus(workflowId, Workflow::archive);
    }

    private Workflow changeStatus(
            WorkflowId workflowId,
            UnaryOperator<Workflow> transition) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        return workflowRepository.save(transition.apply(workflow));
    }
}
```

- [ ] **Step 5: Run application tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=ChangeWorkflowLifecycleServiceTest,CreateWorkflowServiceTest,QueryWorkflowServiceTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit application lifecycle use case if authorized**

```bash
git add backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/port/in/ChangeWorkflowLifecycleUseCase.java \
    backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/service/ChangeWorkflowLifecycleService.java \
    backend/graphpilot-application/src/test/java/com/graphpilot/application/workflow/ChangeWorkflowLifecycleServiceTest.java
git commit -m "feat: add workflow lifecycle use case"
```

---

## Task 3: Add lifecycle fields and endpoints to Spring Web adapter

**Files:**
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowResponse.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowController.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowHttpExceptionHandler.java`
- Modify: `backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/workflow/WorkflowControllerTest.java`

- [ ] **Step 1: Add failing web tests**

In `WorkflowControllerTest`, update the test configuration constructor call for `WorkflowController` to pass a `ChangeWorkflowLifecycleUseCase` test double. Add this field to the test class:

```java
    private final RecordingChangeWorkflowLifecycleUseCase lifecycleUseCase = new RecordingChangeWorkflowLifecycleUseCase();
```

When constructing `WorkflowController`, use:

```java
new WorkflowController(createWorkflowUseCase, queryWorkflowUseCase, lifecycleUseCase)
```

Add these tests:

```java
    @Test
    void getByIdIncludesWorkflowStatus() throws Exception {
        queryWorkflowUseCase.workflow = Optional.of(workflow("workflow-1", WorkflowStatus.ACTIVE));

        mockMvc.perform(get("/api/workflows/workflow-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listIncludesWorkflowStatus() throws Exception {
        queryWorkflowUseCase.workflows = List.of(workflow("workflow-1", WorkflowStatus.PAUSED));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("workflow-1"))
                .andExpect(jsonPath("$[0].status").value("PAUSED"));
    }

    @Test
    void activatesWorkflow() throws Exception {
        lifecycleUseCase.workflow = workflow("workflow-1", WorkflowStatus.ACTIVE);

        mockMvc.perform(post("/api/workflows/workflow-1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void pausesWorkflow() throws Exception {
        lifecycleUseCase.workflow = workflow("workflow-1", WorkflowStatus.PAUSED);

        mockMvc.perform(post("/api/workflows/workflow-1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void resumesWorkflow() throws Exception {
        lifecycleUseCase.workflow = workflow("workflow-1", WorkflowStatus.ACTIVE);

        mockMvc.perform(post("/api/workflows/workflow-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void archivesWorkflow() throws Exception {
        lifecycleUseCase.workflow = workflow("workflow-1", WorkflowStatus.ARCHIVED);

        mockMvc.perform(post("/api/workflows/workflow-1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void lifecycleActionReturnsNotFoundWhenWorkflowIsMissing() throws Exception {
        lifecycleUseCase.notFoundWorkflowId = WorkflowId.of("missing-workflow");

        mockMvc.perform(post("/api/workflows/missing-workflow/activate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workflow not found"));
    }

    @Test
    void lifecycleActionReturnsConflictForInvalidTransition() throws Exception {
        lifecycleUseCase.lifecycleException = new WorkflowLifecycleException(
                "Cannot pause workflow from status DRAFT");

        mockMvc.perform(post("/api/workflows/workflow-1/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid workflow lifecycle transition"))
                .andExpect(jsonPath("$.detail").value("Cannot pause workflow from status DRAFT"));
    }
```

Add this test double at the bottom of `WorkflowControllerTest`:

```java
    private static final class RecordingChangeWorkflowLifecycleUseCase implements ChangeWorkflowLifecycleUseCase {

        private Workflow workflow = workflow("workflow-1", WorkflowStatus.ACTIVE);
        private WorkflowId notFoundWorkflowId;
        private WorkflowLifecycleException lifecycleException;

        @Override
        public Workflow activate(WorkflowId workflowId) {
            return resultFor(workflowId);
        }

        @Override
        public Workflow pause(WorkflowId workflowId) {
            return resultFor(workflowId);
        }

        @Override
        public Workflow resume(WorkflowId workflowId) {
            return resultFor(workflowId);
        }

        @Override
        public Workflow archive(WorkflowId workflowId) {
            return resultFor(workflowId);
        }

        private Workflow resultFor(WorkflowId workflowId) {
            if (lifecycleException != null) {
                throw lifecycleException;
            }
            if (workflowId.equals(notFoundWorkflowId)) {
                throw new WorkflowNotFoundException(workflowId);
            }
            return workflow;
        }
    }
```

Update or add a helper overload:

```java
    private static Workflow workflow(String id, WorkflowStatus status) {
        return Workflow.restore(
                WorkflowId.of(id),
                WorkflowName.of("Daily ETL"),
                new DagDefinition(
                        List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                        List.of()),
                status,
                Instant.parse("2026-06-14T00:00:00Z"));
    }
```

Add imports:

```java
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.domain.workflow.WorkflowLifecycleException;
import com.graphpilot.domain.workflow.WorkflowStatus;
```

- [ ] **Step 2: Run web adapter tests and verify they fail**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test -Dtest=WorkflowControllerTest
```

Expected: compilation fails because controller constructor and response status field are not implemented.

- [ ] **Step 3: Update `WorkflowResponse`**

Replace `WorkflowResponse` with:

```java
package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.workflow.Workflow;
import java.time.Instant;
import java.util.List;

record WorkflowResponse(
        String id,
        String name,
        String status,
        List<TaskResponse> tasks,
        List<EdgeResponse> edges,
        Instant createdAt) {

    static WorkflowResponse from(Workflow workflow) {
        return new WorkflowResponse(
                workflow.id().value(),
                workflow.name().value(),
                workflow.status().name(),
                workflow.dag().tasks().stream()
                        .map(TaskResponse::from)
                        .toList(),
                workflow.dag().edges().stream()
                        .map(EdgeResponse::from)
                        .toList(),
                workflow.createdAt());
    }

    record TaskResponse(String id, String name) {

        static TaskResponse from(TaskDefinition task) {
            return new TaskResponse(task.id().value(), task.name());
        }
    }

    record EdgeResponse(String fromTaskId, String toTaskId) {

        static EdgeResponse from(DagEdge edge) {
            return new EdgeResponse(edge.fromTaskId().value(), edge.toTaskId().value());
        }
    }
}
```

- [ ] **Step 4: Update `WorkflowController`**

Replace `WorkflowController` with:

```java
package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/workflows")
class WorkflowController {

    private static final int MAX_LIST_LIMIT = 100;

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final QueryWorkflowUseCase queryWorkflowUseCase;
    private final ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase;

    WorkflowController(
            CreateWorkflowUseCase createWorkflowUseCase,
            QueryWorkflowUseCase queryWorkflowUseCase,
            ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase) {
        this.createWorkflowUseCase = Objects.requireNonNull(
                createWorkflowUseCase,
                "createWorkflowUseCase must not be null");
        this.queryWorkflowUseCase = Objects.requireNonNull(
                queryWorkflowUseCase,
                "queryWorkflowUseCase must not be null");
        this.changeWorkflowLifecycleUseCase = Objects.requireNonNull(
                changeWorkflowLifecycleUseCase,
                "changeWorkflowLifecycleUseCase must not be null");
    }

    @PostMapping
    ResponseEntity<CreateWorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Workflow request body must not be null");
        }
        WorkflowId workflowId = createWorkflowUseCase.create(request.toCommand());
        URI location = URI.create("/api/workflows/" + workflowId.value());
        return ResponseEntity.created(location)
                .body(new CreateWorkflowResponse(workflowId.value()));
    }

    @GetMapping
    ResponseEntity<List<WorkflowResponse>> list(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        int boundedLimit = validateListLimit(limit);
        return ResponseEntity.ok(queryWorkflowUseCase.findAll(boundedLimit).stream()
                .map(WorkflowResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkflowResponse> getById(@PathVariable("id") String id) {
        WorkflowId workflowId = workflowIdFromPath(id);
        return ResponseEntity.ok(WorkflowResponse.from(queryWorkflowUseCase.findById(workflowId)));
    }

    @PostMapping("/{id}/activate")
    ResponseEntity<WorkflowResponse> activate(@PathVariable("id") String id) {
        return lifecycleResponse(changeWorkflowLifecycleUseCase.activate(workflowIdFromPath(id)));
    }

    @PostMapping("/{id}/pause")
    ResponseEntity<WorkflowResponse> pause(@PathVariable("id") String id) {
        return lifecycleResponse(changeWorkflowLifecycleUseCase.pause(workflowIdFromPath(id)));
    }

    @PostMapping("/{id}/resume")
    ResponseEntity<WorkflowResponse> resume(@PathVariable("id") String id) {
        return lifecycleResponse(changeWorkflowLifecycleUseCase.resume(workflowIdFromPath(id)));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<WorkflowResponse> archive(@PathVariable("id") String id) {
        return lifecycleResponse(changeWorkflowLifecycleUseCase.archive(workflowIdFromPath(id)));
    }

    private static ResponseEntity<WorkflowResponse> lifecycleResponse(Workflow workflow) {
        return ResponseEntity.ok(WorkflowResponse.from(workflow));
    }

    private static WorkflowId workflowIdFromPath(String id) {
        return WorkflowId.of(UriUtils.decode(id, StandardCharsets.UTF_8));
    }

    private static int validateListLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException(
                    "Workflow list limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return limit;
    }
}
```

- [ ] **Step 5: Update exception handler for lifecycle conflict**

Add import:

```java
import com.graphpilot.domain.workflow.WorkflowLifecycleException;
```

Add this method above `badRequestProblem`:

```java
    @ExceptionHandler(WorkflowLifecycleException.class)
    ProblemDetail handleWorkflowLifecycleConflict(WorkflowLifecycleException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Invalid workflow lifecycle transition");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }
```

- [ ] **Step 6: Run web adapter tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test -Dtest=WorkflowControllerTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit web lifecycle API if authorized**

```bash
git add backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowResponse.java \
    backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowController.java \
    backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/workflow/WorkflowHttpExceptionHandler.java \
    backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/workflow/WorkflowControllerTest.java
git commit -m "feat: add workflow lifecycle web endpoints"
```

---

## Task 4: Persist status in memory and MyBatis adapters

**Files:**
- Modify: `backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRepositoryTest.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql`
- Modify: `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java`

- [ ] **Step 1: Add failing memory repository status test**

Add this test to `InMemoryWorkflowRepositoryTest`:

```java
    @Test
    void savesWorkflowStatus() {
        Workflow activeWorkflow = workflow("workflow-active", "Active Workflow").activate();

        repository.save(activeWorkflow);

        assertThat(repository.findById(activeWorkflow.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(WorkflowStatus.ACTIVE));
    }
```

Add import:

```java
import com.graphpilot.domain.workflow.WorkflowStatus;
```

- [ ] **Step 2: Run memory repository test**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-memory -am test -Dtest=InMemoryWorkflowRepositoryTest
```

Expected: `BUILD SUCCESS`. The memory adapter stores full `Workflow` values, so no production memory adapter change is required.

- [ ] **Step 3: Add failing MyBatis status tests**

In `MyBatisWorkflowRepositoryTest`, add import:

```java
import com.graphpilot.domain.workflow.WorkflowStatus;
```

Add tests:

```java
    @Test
    void saveThenFindByIdRestoresWorkflowStatus() {
        Workflow activeWorkflow = workflow("workflow-active", "Active Workflow", CREATED_AT).activate();

        repository.save(activeWorkflow);

        assertThat(repository.findById(activeWorkflow.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(WorkflowStatus.ACTIVE));
    }

    @Test
    void saveUpdatesExistingWorkflowStatus() {
        Workflow draftWorkflow = workflow("workflow-status", "Status Workflow", CREATED_AT);
        Workflow activeWorkflow = draftWorkflow.activate();

        repository.save(draftWorkflow);
        repository.save(activeWorkflow);

        assertThat(repository.findById(activeWorkflow.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(WorkflowStatus.ACTIVE));
    }
```

Add constant near the existing imports/class constants if the file does not already define one:

```java
    private static final Instant CREATED_AT = Instant.parse("2026-06-13T00:00:00Z");
```

If the helper method currently accepts `Instant createdAt`, keep it and use `CREATED_AT` in new tests.

- [ ] **Step 4: Run MyBatis repository test and verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRepositoryTest
```

Expected with Docker available: fails because the database schema and mapper do not include `status`. Expected without Docker: test class is discovered and skipped due to `disabledWithoutDocker = true`; continue with implementation and rely on compile plus non-container tests locally.

- [ ] **Step 5: Update Flyway migration**

In `V1__create_workflow_tables.sql`, change the workflows table from:

```sql
create table workflows (
    id text primary key check (btrim(id) <> ''),
    name text not null check (btrim(name) <> ''),
    created_at timestamptz not null
);
```

to:

```sql
create table workflows (
    id text primary key check (btrim(id) <> ''),
    name text not null check (btrim(name) <> ''),
    status text not null check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    created_at timestamptz not null
);
```

- [ ] **Step 6: Replace `WorkflowRow`**

Replace `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java` with:

```java
package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRow(String id, String name, String status, Instant createdAt) {

    public WorkflowRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
```

- [ ] **Step 7: Update MyBatis mapper XML**

In `WorkflowMapper.xml`, update the workflow result map constructor:

```xml
    <resultMap id="WorkflowRowResultMap" type="com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow">
        <constructor>
            <arg column="id" javaType="java.lang.String" />
            <arg column="name" javaType="java.lang.String" />
            <arg column="status" javaType="java.lang.String" />
            <arg column="created_at" javaType="java.time.Instant" />
        </constructor>
    </resultMap>
```

Update `upsertWorkflow`:

```xml
    <insert id="upsertWorkflow" parameterType="com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow">
        insert into workflows (id, name, status, created_at)
        values (#{id}, #{name}, #{status}, #{createdAt})
        on conflict (id) do update set
            name = excluded.name,
            status = excluded.status,
            created_at = excluded.created_at
    </insert>
```

Update `findWorkflowById` select list:

```xml
        select id, name, status, created_at
```

Update `findAllWorkflows` select list:

```xml
        select id, name, status, created_at
```

- [ ] **Step 8: Update `MyBatisWorkflowRepository` mapping**

Add import:

```java
import com.graphpilot.domain.workflow.WorkflowStatus;
```

Change `toWorkflowRow` to:

```java
    private WorkflowRow toWorkflowRow(Workflow workflow) {
        return new WorkflowRow(
                workflow.id().value(),
                workflow.name().value(),
                workflow.status().name(),
                workflow.createdAt());
    }
```

Change workflow reconstruction from:

```java
        return Workflow.create(
                WorkflowId.of(workflowRow.id()),
                WorkflowName.of(workflowRow.name()),
                new DagDefinition(tasks, edges),
                workflowRow.createdAt());
```

to:

```java
        return Workflow.restore(
                WorkflowId.of(workflowRow.id()),
                WorkflowName.of(workflowRow.name()),
                new DagDefinition(tasks, edges),
                WorkflowStatus.valueOf(workflowRow.status()),
                workflowRow.createdAt());
```

- [ ] **Step 9: Run persistence adapter tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-memory -am test -Dtest=InMemoryWorkflowRepositoryTest
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRepositoryTest
```

Expected: memory repository test passes. MyBatis test passes when Docker is available; when Docker is unavailable, report the skipped Testcontainers class.

- [ ] **Step 10: Commit persistence status changes if authorized**

```bash
git add backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRepositoryTest.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml \
    backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql \
    backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java
git commit -m "feat: persist workflow lifecycle status"
```

---

## Task 5: Wire lifecycle use case into bootstrap and integration tests

**Files:**
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/GraphPilotApplicationTest.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/CreateWorkflowApiIntegrationTest.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/QueryWorkflowApiIntegrationTest.java`
- Modify: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java`

- [ ] **Step 1: Add failing bootstrap bean test**

In `GraphPilotApplicationTest`, add import:

```java
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
```

Add field:

```java
    @Autowired
    private ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase;
```

Add assertion in `loadsWorkflowBeans`:

```java
        assertThat(changeWorkflowLifecycleUseCase).isNotNull();
```

- [ ] **Step 2: Update API integration tests for status**

In `CreateWorkflowApiIntegrationTest`, after creating and retrieving a workflow, add an assertion on the retrieved response body:

```java
        assertThat(getResponse.getBody()).containsEntry("status", "DRAFT");
```

In `QueryWorkflowApiIntegrationTest`, update get/list response assertions to include:

```java
        assertThat(response.getBody()).containsEntry("status", "DRAFT");
```

For list responses, assert each created workflow has status:

```java
        assertThat(listResponse.getBody())
                .filteredOn(workflow -> workflow.get("id").equals(workflowId))
                .singleElement()
                .satisfies(workflow -> assertThat(workflow).containsEntry("status", "DRAFT"));
```

In `PostgresWorkflowApiIntegrationTest`, after create, activate through HTTP:

```java
        ResponseEntity<Map<String, Object>> activateResponse = lifecycleAction(workflowId, "activate");
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody()).containsEntry("status", "ACTIVE");
```

Add helper method to `PostgresWorkflowApiIntegrationTest`:

```java
    private ResponseEntity<Map<String, Object>> lifecycleAction(String workflowId, String action) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId + "/" + action,
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }
```

Update `assertWorkflow` in `PostgresWorkflowApiIntegrationTest` to accept expected status:

```java
    private static void assertWorkflow(Map<String, Object> workflow, String workflowId, String expectedStatus) {
        assertThat(workflow).containsEntry("id", workflowId);
        assertWorkflow(workflow, expectedStatus);
    }

    private static void assertWorkflow(Map<String, Object> workflow, String expectedStatus) {
        assertThat(workflow).containsEntry("name", "Postgres ETL");
        assertThat(workflow).containsEntry("status", expectedStatus);
        assertThat(workflow).containsKey("createdAt");
        List<?> tasks = (List<?>) workflow.get("tasks");
        assertThat(tasks).hasSize(3);
        assertThat(tasks.stream()
                        .map(task -> ((Map<?, ?>) task).get("id").toString())
                        .toList())
                .containsExactly("extract", "transform", "load");

        List<?> edges = (List<?>) workflow.get("edges");
        assertThat(edges).hasSize(2);
        assertThat(edges.stream()
                        .map(edge -> ((Map<?, ?>) edge).get("fromTaskId") + "->" + ((Map<?, ?>) edge).get("toTaskId"))
                        .toList())
                .containsExactly("extract->transform", "transform->load");
    }
```

Then update callers after activation to pass `"ACTIVE"`.

- [ ] **Step 3: Run bootstrap tests and verify they fail**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=GraphPilotApplicationTest,CreateWorkflowApiIntegrationTest,QueryWorkflowApiIntegrationTest,PostgresWorkflowApiIntegrationTest
```

Expected: compilation or context failure because lifecycle use case bean is not wired yet.

- [ ] **Step 4: Wire lifecycle use case bean**

In `WorkflowAssemblyConfiguration`, add imports:

```java
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.service.ChangeWorkflowLifecycleService;
```

Add bean after `QueryWorkflowUseCase` bean:

```java
    @Bean
    ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase(WorkflowRepository workflowRepository) {
        return new ChangeWorkflowLifecycleService(workflowRepository);
    }
```

- [ ] **Step 5: Run bootstrap tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=GraphPilotApplicationTest,CreateWorkflowApiIntegrationTest,QueryWorkflowApiIntegrationTest,PostgresWorkflowApiIntegrationTest
```

Expected: `BUILD SUCCESS` when Docker is available. When Docker is unavailable, `PostgresWorkflowApiIntegrationTest` is skipped and non-container tests pass.

- [ ] **Step 6: Run non-container bootstrap tests explicitly if Docker is unavailable**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=!PostgresWorkflowApiIntegrationTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit bootstrap lifecycle wiring if authorized**

```bash
git add backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/GraphPilotApplicationTest.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/CreateWorkflowApiIntegrationTest.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/QueryWorkflowApiIntegrationTest.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java
git commit -m "feat: wire workflow lifecycle bootstrap"
```

---

## Task 6: Full verification and mandatory reviews

**Files:**
- Verify all changed backend files.

- [ ] **Step 1: Run Maven validate**

Run:

```bash
mvn -f backend/pom.xml validate
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run Maven compile**

Run:

```bash
mvn -f backend/pom.xml compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run full backend tests**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: `BUILD SUCCESS`. If Docker is unavailable, MyBatis/Postgres Testcontainers tests may be skipped; report exact skipped counts from Surefire reports.

- [ ] **Step 4: Inspect Surefire reports for container test status**

Read these files if they exist:

```text
backend/graphpilot-adapter-persistence-mybatis/target/surefire-reports/com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRepositoryTest.txt
backend/graphpilot-bootstrap-spring/target/surefire-reports/com.graphpilot.bootstrap.spring.PostgresWorkflowApiIntegrationTest.txt
```

Expected with Docker unavailable: skipped counts are non-zero and failures/errors are zero.

- [ ] **Step 5: Inspect git status and diff**

Run:

```bash
git status --short --branch
git diff --stat
```

Expected: only files listed in this plan are changed.

- [ ] **Step 6: Run mandatory code review agents**

Use review agents after code changes:

1. `ecc:java-reviewer` for Java/Spring/MyBatis correctness.
2. `ecc:security-reviewer` because lifecycle API inputs and database schema are changed.
3. `ecc:database-reviewer` because Flyway schema and MyBatis mapper are changed.

Address all CRITICAL and HIGH findings. Re-run targeted tests after each fix.

- [ ] **Step 7: Final aggregate commit if task commits were skipped**

If earlier task commits were not created, run:

```bash
git add backend
git commit -m "feat: add workflow lifecycle status"
```

If task-by-task commits already exist, skip this step.

---

## Self-Review

- Spec coverage:
  - Status enum and default `DRAFT`: Task 1.
  - Legal transitions and immutable methods: Task 1.
  - Illegal transitions as domain exception: Task 1 and Task 3.
  - Application lifecycle port/service: Task 2.
  - Action endpoints: Task 3.
  - `409 Conflict`: Task 3.
  - Response `status` in get/list/actions: Task 3 and Task 5.
  - Memory and MyBatis persistence: Task 4.
  - Bootstrap wiring and integration tests: Task 5.
  - Full verification and reviews: Task 6.
- Placeholder scan: no incomplete implementation instructions are intentionally left.
- Type consistency:
  - `WorkflowStatus` values are `DRAFT`, `ACTIVE`, `PAUSED`, `ARCHIVED` everywhere.
  - Lifecycle port methods are `activate`, `pause`, `resume`, `archive` in application, controller, and tests.
  - `Workflow.restore(...)` signature is consistent across domain tests, application tests, and MyBatis mapping.
  - API response field is `status`, represented as enum `.name()` string.
