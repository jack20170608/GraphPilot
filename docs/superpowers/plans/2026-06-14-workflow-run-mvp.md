# Workflow Run MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a manual Workflow Run MVP that creates and queries workflow run/task-run metadata for ACTIVE workflows.

**Architecture:** Add run metadata as a separate execution domain, expose trigger/query use cases through application ports, and implement memory/MyBatis repositories plus Spring Web endpoints. This slice creates `PENDING` run records only; it does not schedule or execute tasks.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot 3.3.5, MyBatis XML mapper, Flyway, JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL.

---

## Source Design

Implement:

- `docs/superpowers/specs/2026-06-14-workflow-run-mvp-design.md`

## Scope and Safety Notes

- Do not implement scheduler, worker execution, retry, cancel, timeout, logs, outputs, API envelope, auth, tenant, or run status update APIs.
- Domain/application modules must remain framework-free.
- Persistence schema must use new Flyway V3; do not edit V1/V2.
- Keep Workflow definition lifecycle separate from Workflow Run status.
- Docker-dependent Testcontainers tests may skip when Docker is unavailable; report exact skipped counts.

## File Structure

### Create

- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunId.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TaskRunId.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunStatus.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TaskRunStatus.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunTriggerException.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRun.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TaskRun.java`
- `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/execution/WorkflowRunTest.java`
- `backend/graphpilot-domain/src/test/java/com/graphpilot/domain/execution/TaskRunTest.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/WorkflowRunNotFoundException.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/TriggerWorkflowRunUseCase.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/QueryWorkflowRunUseCase.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/WorkflowRunRepository.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/WorkflowRunIdGeneratorPort.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/TaskRunIdGeneratorPort.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/TriggerWorkflowRunService.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/QueryWorkflowRunService.java`
- `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/TriggerWorkflowRunServiceTest.java`
- `backend/graphpilot-application/src/test/java/com/graphpilot/application/execution/QueryWorkflowRunServiceTest.java`
- `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunRepository.java`
- `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidWorkflowRunIdGenerator.java`
- `backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidTaskRunIdGenerator.java`
- `backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunRepositoryTest.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V3__create_workflow_run_tables.sql`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRunRow.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/TaskRunRow.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.xml`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunRepository.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunRepositoryTest.java`
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/CreateWorkflowRunResponse.java`
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunResponse.java`
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/TaskRunResponse.java`
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunHttpExceptionHandler.java`
- `backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunController.java`
- `backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/execution/WorkflowRunControllerTest.java`
- `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowRunAssemblyConfiguration.java`
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/WorkflowRunApiIntegrationTest.java`
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowRunApiIntegrationTest.java`

---

## Task 1: Add execution domain model

**Files:**
- Create domain execution files and tests listed above for domain only.

- [ ] **Step 1: Write failing domain tests**

Create `WorkflowRunTest` and `TaskRunTest` covering:

```java
// WorkflowRunTest
@Test void createsPendingRunForActiveWorkflow()
@Test void rejectsNonActiveWorkflowTrigger()
@Test void rejectsMissingRequiredFields()
@Test void workflowRunIdTrimsAndRejectsBlank()

// TaskRunTest
@Test void createsPendingTaskRunFromTaskSnapshot()
@Test void rejectsNegativePosition()
@Test void taskRunIdTrimsAndRejectsBlank()
@Test void rejectsMissingRequiredFields()
```

Use `Workflow.create(...).activate()` for active workflow and `WorkflowStatus.DRAFT/PAUSED/ARCHIVED` examples through domain transitions.

- [ ] **Step 2: Verify red**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=WorkflowRunTest,TaskRunTest
```

Expected: compilation fails because execution domain types do not exist.

- [ ] **Step 3: Implement domain execution types**

Create records/enums:

- `WorkflowRunId` and `TaskRunId`: same trim/nonblank style as `WorkflowId`.
- `WorkflowRunStatus`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELED`.
- `TaskRunStatus`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`.
- `WorkflowRunTriggerException extends RuntimeException`.
- `WorkflowRun` record with create/restore.
- `TaskRun` record with create/restore.

Required behavior:

```java
WorkflowRun.create(id, workflow, triggeredAt)
```

only accepts `workflow.status() == WorkflowStatus.ACTIVE`, otherwise throws `WorkflowRunTriggerException("Workflow run can only be triggered for ACTIVE workflow")`.

`TaskRun.create(...)` creates `PENDING`, rejects negative position with `IllegalArgumentException("Task run position must not be negative")`.

- [ ] **Step 4: Verify green**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-domain test -Dtest=WorkflowRunTest,TaskRunTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution \
    backend/graphpilot-domain/src/test/java/com/graphpilot/domain/execution
git commit -m "feat: add workflow run domain model"
```

---

## Task 2: Add execution application ports and services

**Files:**
- Create application execution files and tests listed above.

- [ ] **Step 1: Write failing service tests**

Create `TriggerWorkflowRunServiceTest` covering:

- active workflow creates one run and task runs for each workflow task.
- task run order follows `workflow.dag().tasks()` order.
- missing workflow throws `WorkflowNotFoundException`.
- non-active workflow throws `WorkflowRunTriggerException` and does not save.
- null workflow id throws NPE message `workflowId must not be null`.

Create `QueryWorkflowRunServiceTest` covering:

- findRunById returns existing run.
- findRunById missing throws `WorkflowRunNotFoundException`.
- findRunsByWorkflowId rejects non-positive limit.
- findTaskRunsByRunId confirms run exists before returning task runs.

Use in-test recording repositories/id generators/clock.

- [ ] **Step 2: Verify red**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=TriggerWorkflowRunServiceTest,QueryWorkflowRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because application execution types do not exist.

- [ ] **Step 3: Implement ports/exceptions/services**

Create:

- `WorkflowRunNotFoundException` message `Workflow run not found: id=<id>`.
- inbound/outbound ports as described in spec.
- `TriggerWorkflowRunService`.
- `QueryWorkflowRunService`.

Implementation notes:

- Generate `WorkflowRunId` once per trigger.
- Use `ClockPort.now()` once for run/task created timestamps.
- Generate one `TaskRunId` per task.
- Save run + taskRuns through repository.
- Return saved run id.

- [ ] **Step 4: Verify green**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-application -am test -Dtest=TriggerWorkflowRunServiceTest,QueryWorkflowRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-application/src/main/java/com/graphpilot/application/execution \
    backend/graphpilot-application/src/test/java/com/graphpilot/application/execution
git commit -m "feat: add workflow run application services"
```

---

## Task 3: Add memory execution adapter

**Files:**
- Create memory repository/id generator files and tests.

- [ ] **Step 1: Write failing memory adapter tests**

Create `InMemoryWorkflowRunRepositoryTest` covering:

- save and find run by id.
- find runs by workflow id sorted by `triggeredAt`, then id, with limit.
- find task runs by run id sorted by position then task id.
- non-positive limit rejected.
- returned lists are defensive copies.

- [ ] **Step 2: Verify red**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-memory -am test -Dtest=InMemoryWorkflowRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because memory run repository does not exist.

- [ ] **Step 3: Implement memory repository and UUID generators**

Implement `InMemoryWorkflowRunRepository` with `ConcurrentHashMap` and defensive copies.

Implement:

- `UuidWorkflowRunIdGenerator` returns `WorkflowRunId.of(UUID.randomUUID().toString())`.
- `UuidTaskRunIdGenerator` returns `TaskRunId.of(UUID.randomUUID().toString())`.

- [ ] **Step 4: Verify green**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-memory -am test -Dtest=InMemoryWorkflowRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunRepository.java \
    backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidWorkflowRunIdGenerator.java \
    backend/graphpilot-adapter-persistence-memory/src/main/java/com/graphpilot/adapter/persistence/memory/UuidTaskRunIdGenerator.java \
    backend/graphpilot-adapter-persistence-memory/src/test/java/com/graphpilot/adapter/persistence/memory/InMemoryWorkflowRunRepositoryTest.java
git commit -m "feat: add in-memory workflow run repository"
```

---

## Task 4: Add MyBatis execution adapter

**Files:**
- Create V3 migration, row records, mapper interface/XML, repository, Testcontainers test.

- [ ] **Step 1: Write failing MyBatis repository test**

Create `MyBatisWorkflowRunRepositoryTest` using the existing MyBatis test style. It should:

- save workflow definition first through `WorkflowMapper` or existing `WorkflowRepository` bean if available.
- save run + task runs through `WorkflowRunRepository`.
- find run by id restores fields.
- find workflow runs by workflow id sorted and limited.
- find task runs by run id sorted by position.
- missing run returns empty.
- non-positive limit rejected.

- [ ] **Step 2: Verify red or Docker skip**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected with Docker: compilation/config failure before implementation. Without Docker: class may skip after compile; still continue implementation.

- [ ] **Step 3: Add V3 migration**

Create `V3__create_workflow_run_tables.sql` exactly following the design schema.

- [ ] **Step 4: Add row records and mapper**

Create `WorkflowRunRow`, `TaskRunRow`, `WorkflowRunMapper`, and `WorkflowRunMapper.xml`.

Mapper methods:

```java
void insertWorkflowRun(WorkflowRunRow workflowRun);
void insertTaskRuns(@Param("taskRuns") List<TaskRunRow> taskRuns);
WorkflowRunRow findWorkflowRunById(@Param("workflowRunId") String workflowRunId);
List<WorkflowRunRow> findWorkflowRunsByWorkflowId(@Param("workflowId") String workflowId, @Param("limit") int limit);
List<TaskRunRow> findTaskRunsByRunId(@Param("workflowRunId") String workflowRunId);
```

- [ ] **Step 5: Implement MyBatis repository**

`MyBatisWorkflowRunRepository` implements `WorkflowRunRepository`:

- `save` inserts run, then task runs if list not empty, returns run.
- `findRunById` maps row to domain.
- `findRunsByWorkflowId` validates positive limit.
- `findTaskRunsByRunId` maps rows to domain.
- Use `WorkflowRun.restore(...)` and `TaskRun.restore(...)`.

- [ ] **Step 6: Verify targeted MyBatis tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: passes with Docker; skips with Docker unavailable and zero failures.

- [ ] **Step 7: Commit**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V3__create_workflow_run_tables.sql \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRunRow.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/TaskRunRow.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.xml \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunRepository.java \
    backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRunRepositoryTest.java
git commit -m "feat: add mybatis workflow run repository"
```

---

## Task 5: Add Spring Web run API

**Files:**
- Create web execution controller/DTOs/handler/test.

- [ ] **Step 1: Write failing web tests**

Create `WorkflowRunControllerTest` with standalone MockMvc and test doubles for trigger/query use cases. Cover:

- trigger returns 201, Location, id.
- missing workflow returns 404.
- non-active workflow returns 409.
- list workflow runs returns 200.
- get run returns 200.
- get missing run returns 404.
- get task runs returns 200.
- invalid limit returns 400.

- [ ] **Step 2: Verify red**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test -Dtest=WorkflowRunControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because web execution controller/DTOs do not exist.

- [ ] **Step 3: Implement web execution API**

Create DTO records and `WorkflowRunController`.

Endpoints:

- `POST /api/workflows/{workflowId}/runs`
- `GET /api/workflows/{workflowId}/runs?limit=50`
- `GET /api/workflow-runs/{runId}`
- `GET /api/workflow-runs/{runId}/tasks`

Create `WorkflowRunHttpExceptionHandler` mapping:

- `WorkflowRunNotFoundException` -> 404 title `Workflow run not found`.
- `WorkflowRunTriggerException` -> 409 title `Workflow run cannot be triggered`.
- `IllegalArgumentException` -> 400 title `Invalid workflow run request`.

- [ ] **Step 4: Verify green**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-web-spring -am test -Dtest=WorkflowRunControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-adapter-web-spring/src/main/java/com/graphpilot/adapter/web/spring/execution \
    backend/graphpilot-adapter-web-spring/src/test/java/com/graphpilot/adapter/web/spring/execution
git commit -m "feat: add workflow run web api"
```

---

## Task 6: Wire bootstrap and integration tests

**Files:**
- Create bootstrap run assembly and integration tests.
- Modify MyBatis `WorkflowPersistenceConfiguration` if needed to expose `WorkflowRunRepository` under postgres profile.

- [ ] **Step 1: Write failing bootstrap integration tests**

Create `WorkflowRunApiIntegrationTest` covering memory profile flow:

1. POST create workflow.
2. POST activate workflow.
3. POST trigger run.
4. GET run.
5. GET task runs.
6. GET workflow run list.

Create `PostgresWorkflowRunApiIntegrationTest` with the same flow under `postgres` profile and Testcontainers.

- [ ] **Step 2: Verify red**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=WorkflowRunApiIntegrationTest,PostgresWorkflowRunApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: context fails because run beans are not wired; Postgres may skip without Docker after compile.

- [ ] **Step 3: Add bootstrap assembly**

Create `WorkflowRunAssemblyConfiguration`:

- default profile bean `WorkflowRunRepository` -> `InMemoryWorkflowRunRepository`.
- beans for `WorkflowRunIdGeneratorPort`, `TaskRunIdGeneratorPort` using UUID adapters.
- `TriggerWorkflowRunUseCase` bean.
- `QueryWorkflowRunUseCase` bean.

Update `WorkflowPersistenceConfiguration` to provide `WorkflowRunRepository` with `MyBatisWorkflowRunRepository` under postgres profile if mapper scan does not cover it automatically.

- [ ] **Step 4: Verify green**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=WorkflowRunApiIntegrationTest,PostgresWorkflowRunApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: memory test passes; Postgres passes with Docker or skips with zero failures.

If Docker unavailable, also run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=!PostgresWorkflowRunApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 5: Commit**

```bash
git add backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowRunAssemblyConfiguration.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/WorkflowRunApiIntegrationTest.java \
    backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowRunApiIntegrationTest.java \
    backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/WorkflowPersistenceConfiguration.java
git commit -m "feat: wire workflow run bootstrap"
```

---

## Task 7: Verification, reviews, docs

**Files:**
- Modify `README.md` and `docs/architecture/overview.md` if current feature status needs updating.

- [ ] **Step 1: Update docs**

Update Chinese docs to mention Workflow Run MVP endpoints and clarify no scheduler/worker yet.

- [ ] **Step 2: Run full verification**

Run:

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
mvn -f backend/pom.xml test
```

Expected: `BUILD SUCCESS`; container tests may skip if Docker unavailable.

- [ ] **Step 3: Inspect Testcontainers reports**

Read relevant Surefire report files for MyBatis/Postgres run tests and report skipped counts.

- [ ] **Step 4: Run mandatory reviewers**

Use:

- `ecc:java-reviewer`
- `ecc:security-reviewer`
- `ecc:database-reviewer`

Address CRITICAL/HIGH findings.

- [ ] **Step 5: Commit docs/fixes**

```bash
git add README.md docs/architecture/overview.md
git commit -m "docs: document workflow run mvp"
```

If no docs changed, skip commit.

---

## Self-Review

- Spec coverage:
  - Manual trigger for ACTIVE workflows: Tasks 1, 2, 5, 6.
  - Run/task-run domain metadata: Task 1.
  - Query single run, workflow run list, task runs: Tasks 2, 3, 4, 5, 6.
  - Memory and MyBatis persistence: Tasks 3 and 4.
  - Spring Web endpoints: Task 5.
  - Bootstrap integration: Task 6.
  - Verification/reviews/docs: Task 7.
- Placeholder scan: no incomplete instructions are intentionally left.
- Type consistency:
  - `WorkflowRunStatus` and `TaskRunStatus` values match schema checks.
  - Endpoint paths match design.
  - Repository methods match application services.
