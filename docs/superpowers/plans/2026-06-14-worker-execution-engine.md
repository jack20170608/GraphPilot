# Worker Execution Engine Implementation Plan

**Goal:** 实现 Worker 执行引擎，支持对 PENDING 的 Workflow Run 进行 DAG 任务执行。

**架构:** Worker 通过事件驱动（ApplicationEvent）监听新创建的 WorkflowRun，解析 DAG 依赖，按拓扑序执行 Task，并支持可扩展的 Handler 机制和重试。

**Tech Stack:** Java 21, Maven multi-module, Spring ApplicationEvent, 自研 Handler 机制, MyBatis XML mapper, Flyway, JUnit 5.

---

## Scope and Safety Notes

- 通过 Spring Events 实现事件驱动 Worker。
- 不实现分布式 Worker 或多节点协调（单一节点 MVP）。
- Domain/Application 模块保持 framework-free（通过 Port 隔离）。
- Persistence schema 使用新 Flyway V4。
- Worker 执行逻辑在 adapter 层实现。
- 支持 Docker Testcontainers 测试。

## Design Summary

### Worker 执行流程

1. **TriggerWorkflowRunService** 在创建 WorkflowRun 后发布 `WorkflowRunCreatedEvent`
2. **Worker** 监听事件，获取 PENDING 的 run
3. **Worker** 从 DAG 解析依赖，按拓扑序调度 Task
4. **TaskHandler** 根据 task type 执行具体操作
5. 更新 TaskRun 状态：`PENDING` → `RUNNING` → `SUCCEEDED`/`FAILED`
6. 所有 Task 完成时更新 WorkflowRun 状态：`PENDING` → `RUNNING` → `SUCCEEDED`/`FAILED`

### Task Type & Handler 机制

- `TaskDefinition` 添加 `type: String` 字段（如 `http`, `shell`, `function`）
- 注册 `TaskHandler` 接口实现，通过 `Map<taskType, TaskHandler>` 分发
- MVP 支持：
  - `http`: 发送 HTTP 请求
  - `shell`: 执行本地 shell 命令
  - `mock`: 模拟执行（用于测试）

### Retry 机制

- TaskRun 添加 `retryCount` 字段
- 失败时检查 `retryCount < maxRetries`，则重试
- 重试间隔可通过配置（简单 MVP 使用固定间隔）

### 数据库 Schema (V4)

```sql
-- TaskRuns 添加 retry 字段
ALTER TABLE task_runs ADD COLUMN retry_count integer not null default 0;
ALTER TABLE task_runs ADD COLUMN max_retries integer not null default 3;
ALTER TABLE task_runs ADD COLUMN error_message text;
ALTER TABLE task_runs ADD COLUMN attempted_at timestamptz;

-- 记录每个 task 的输入输出
ALTER TABLE task_runs ADD COLUMN input_data jsonb;
ALTER TABLE task_runs ADD COLUMN output_data jsonb;

-- 记录 task 开始/结束时间
ALTER TABLE task_runs ADD COLUMN started_at timestamptz;
ALTER TABLE task_runs ADD COLUMN finished_at timestamptz;

-- WorkflowRuns 添加开始/结束时间
ALTER TABLE workflow_runs ADD COLUMN started_at timestamptz;
ALTER TABLE workflow_runs ADD COLUMN finished_at timestamptz;
```

## File Structure

### Create

- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/WorkflowRunCreatedEvent.java`
- `backend/graphpilot-domain/src/main/java/com/graphpilot/domain/execution/TaskResult.java`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V4__add_worker_fields.sql`
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRunRow.java` (update)
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/TaskRunRow.java` (update)
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.java` (update)
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowRunMapper.xml` (update)
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/ExecuteWorkflowRunUseCase.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/in/TaskHandler.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/TaskRunRepository.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/port/out/EventPublisherPort.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/WorkflowExecutionCoordinatorService.java`
- `backend/graphpilot-application/src/main/java/com/graphpilot/application/execution/service/RetryService.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/WorkerConfiguration.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/WorkflowRunEventListener.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/WorkflowExecutionCoordinator.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/handler/TaskHandlerRegistry.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/handler/HttpTaskHandler.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/handler/ShellTaskHandler.java`
- `backend/graphpilot-adapter-worker-spring/src/main/java/com/graphpilot/adapter/worker/spring/handler/MockTaskHandler.java`
- `backend/graphpilot-adapter-worker-spring/src/test/java/com/graphpilot/adapter/worker/spring/WorkflowExecutionCoordinatorTest.java`
- `backend/graphpilot-adapter-worker-spring/src/test/java/com/graphpilot/adapter/worker/spring/handler/HttpTaskHandlerTest.java`
- `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkerAssemblyConfiguration.java`
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/WorkerIntegrationTest.java`

## Tasks

### Task 1: Add domain events and update schema

- [ ] Create `WorkflowRunCreatedEvent` in domain
- [ ] Create `TaskResult` value object
- [ ] Add V4 migration for worker fields
- [ ] Update `TaskRunRow` and `WorkflowRunRow` with new fields
- [ ] Update MyBatis mapper XML for new fields
- [ ] Run tests to verify schema

### Task 2: Add application ports and services

- [ ] Add `TaskHandler` interface in application
- [ ] Add `TaskRunRepository` for status update
- [ ] Add `EventPublisherPort` interface
- [ ] Add `ExecuteWorkflowRunUseCase`
- [ ] Add `WorkflowExecutionCoordinatorService`
- [ ] Add `RetryService`
- [ ] Write unit tests

### Task 3: Implement Worker adapter

- [ ] Create `graphpilot-adapter-worker-spring` module
- [ ] Add `WorkerConfiguration`
- [ ] Add `WorkflowRunEventListener` 
- [ ] Add `WorkflowExecutionCoordinator`
- [ ] Add `TaskHandlerRegistry`
- [ ] Implement `HttpTaskHandler`
- [ ] Implement `ShellTaskHandler`
- [ ] Implement `MockTaskHandler`
- [ ] Write tests

### Task 4: Wire bootstrap

- [ ] Add new module to backend pom
- [ ] Add assembly config
- [ ] Update trigger to publish event
- [ ] Integration test

### Task 5: Verify and document

- [ ] Run full test suite
- [ ] Update docs
- [ ] Manual verification

## Acceptance

1. POST trigger 后 Worker 自动开始执行
2. Task 按 DAG 拓扑序执行，依赖的 Task 先完成
3. 完成后 WorkflowRun 状态为 SUCCEEDED/FAILED
4. Task 失败自动重试（最多 3 次）
5. HTTP/Shell/Mock handler 正常工作
6. Event 驱动机制正常工作