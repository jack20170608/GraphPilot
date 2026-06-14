# Workflow Run / Execution MVP 设计

日期：2026-06-14

## 背景

GraphPilot 当前已经具备 Workflow 定义的 create/get/list、PostgreSQL/MyBatis 持久化，以及 Workflow lifecycle/status：`DRAFT`、`ACTIVE`、`PAUSED`、`ARCHIVED`。

下一步需要从“定义管理”推进到“运行记录”。本 slice 实现最小 Workflow Run 能力：用户可以手动触发一个 `ACTIVE` Workflow，系统创建一次 Workflow Run 和对应 Task Run 记录，并提供查询 API。该 slice 不执行真实任务，不引入 scheduler 或 worker，只建立 execution metadata store 的第一层。

## 目标

1. 支持手动触发 `ACTIVE` Workflow 生成一次 Workflow Run。
2. 触发时为 Workflow 中每个 task 生成一个 Task Run 快照。
3. Workflow Run 初始状态为 `PENDING`。
4. Task Run 初始状态为 `PENDING`。
5. 支持查询单个 Workflow Run。
6. 支持查询某个 Workflow 下的 Run 列表。
7. 支持查询某个 Run 下的 Task Run 列表。
8. 同时支持 in-memory 和 PostgreSQL/MyBatis persistence。
9. 保持 Hexagonal Architecture：domain/application 不依赖 Spring、MyBatis、HTTP DTO 或数据库 row。

## 非目标

本 slice 不实现：

- Scheduler。
- Worker 或真实 task execution。
- Run 状态推进 API。
- Task Run 状态推进 API。
- Retry、timeout、cancel。
- Cron/trigger 配置。
- 日志、输出、错误堆栈或 artifact 存储。
- Auth、tenant、permission、rate limit。
- API response envelope。
- 分页游标或 total count。
- 乐观锁、分布式锁或并发抢占。

## Domain 设计

新增 package：`com.graphpilot.domain.execution`。

### Value Objects

- `WorkflowRunId`
  - 包装 `String value`。
  - 拒绝 null、blank。
  - trim 输入。
- `TaskRunId`
  - 包装 `String value`。
  - 拒绝 null、blank。
  - trim 输入。

### Status Enums

`WorkflowRunStatus`：

```java
PENDING,
RUNNING,
SUCCEEDED,
FAILED,
CANCELED
```

MVP 只创建 `PENDING`。其它状态为后续执行状态推进预留。

`TaskRunStatus`：

```java
PENDING,
RUNNING,
SUCCEEDED,
FAILED,
SKIPPED
```

MVP 只创建 `PENDING`。

### WorkflowRun

字段：

- `WorkflowRunId id`
- `WorkflowId workflowId`
- `WorkflowRunStatus status`
- `Instant triggeredAt`

创建规则：

- `WorkflowRun.create(id, workflow, triggeredAt)` 只接受 `workflow.status() == ACTIVE`。
- 非 `ACTIVE` Workflow 触发时抛出 `WorkflowRunTriggerException`。
- 初始状态固定为 `PENDING`。
- 使用 `WorkflowId` 引用 Workflow 定义，不嵌入完整 Workflow。

### TaskRun

字段：

- `TaskRunId id`
- `WorkflowRunId workflowRunId`
- `TaskId taskId`
- `String taskName`
- `TaskRunStatus status`
- `int position`
- `Instant createdAt`

创建规则：

- `TaskRun.create(id, workflowRunId, task, position, createdAt)`。
- 初始状态固定为 `PENDING`。
- `position >= 0`。
- `taskName` 保存触发瞬间的 task name 快照，避免未来 Workflow 定义变更影响历史 Run 展示。

## Application 设计

新增 package：`com.graphpilot.application.execution`。

### Inbound Ports

`TriggerWorkflowRunUseCase`：

```java
WorkflowRunId trigger(WorkflowId workflowId);
```

`QueryWorkflowRunUseCase`：

```java
WorkflowRun findRunById(WorkflowRunId workflowRunId);
List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit);
List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);
```

### Outbound Ports

`WorkflowRunRepository`：

```java
WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns);
Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId);
List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit);
List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);
```

`WorkflowRunIdGeneratorPort`：

```java
WorkflowRunId nextWorkflowRunId();
```

`TaskRunIdGeneratorPort`：

```java
TaskRunId nextTaskRunId();
```

### Services

`TriggerWorkflowRunService` 依赖：

- `WorkflowRepository`
- `WorkflowRunRepository`
- `WorkflowRunIdGeneratorPort`
- `TaskRunIdGeneratorPort`
- `ClockPort`

流程：

1. 校验 `workflowId` 非空。
2. 从 `WorkflowRepository.findById(workflowId)` 读取 Workflow。
3. 不存在时抛 `WorkflowNotFoundException`。
4. 通过 domain `WorkflowRun.create(...)` 校验 Workflow 必须是 `ACTIVE`。
5. 按 `workflow.dag().tasks()` 顺序生成 Task Run。
6. 调用 `WorkflowRunRepository.save(workflowRun, taskRuns)`。
7. 返回保存后的 `WorkflowRunId`。

`QueryWorkflowRunService` 依赖 `WorkflowRunRepository`，负责：

- 查询不存在的 Run 时抛 `WorkflowRunNotFoundException`。
- 校验 list limit 必须为正数。
- 查询 task runs 前可先确认 run 存在，避免 missing run 返回空列表造成歧义。

## REST API 设计

新增 Spring Web package：`com.graphpilot.adapter.web.spring.execution`。

### 触发 Run

```http
POST /api/workflows/{workflowId}/runs
```

成功：

- `201 Created`
- `Location: /api/workflow-runs/{runId}`
- body：

```json
{ "id": "run-id" }
```

错误：

- Workflow 不存在：`404 Not Found`
- Workflow 非 `ACTIVE`：`409 Conflict`

### 查询某 Workflow 下的 Runs

```http
GET /api/workflows/{workflowId}/runs?limit=50
```

成功：`200 OK`

```json
[
  {
    "id": "run-id",
    "workflowId": "workflow-id",
    "status": "PENDING",
    "triggeredAt": "2026-06-14T00:00:00Z"
  }
]
```

limit 规则沿用 Workflow list：默认 `50`，最大 `100`，非法返回 `400 Bad Request`。

### 查询单个 Run

```http
GET /api/workflow-runs/{runId}
```

成功：`200 OK`，body 为 `WorkflowRunResponse`。

不存在：`404 Not Found`。

### 查询 Task Runs

```http
GET /api/workflow-runs/{runId}/tasks
```

成功：`200 OK`

```json
[
  {
    "id": "task-run-id",
    "workflowRunId": "run-id",
    "taskId": "extract",
    "taskName": "Extract data",
    "status": "PENDING",
    "position": 0,
    "createdAt": "2026-06-14T00:00:00Z"
  }
]
```

Run 不存在：`404 Not Found`。

## Persistence 设计

### In-memory

新增：

- `InMemoryWorkflowRunRepository`
- `UuidWorkflowRunIdGenerator`
- `UuidTaskRunIdGenerator`

存储：

- `Map<WorkflowRunId, WorkflowRun>` 保存 run。
- `Map<WorkflowRunId, List<TaskRun>>` 保存 task runs。
- save 时 defensive copy。
- 查询时返回 copy。
- `findRunsByWorkflowId` 按 `triggeredAt asc, id asc` 排序并应用 limit。
- task runs 按 `position asc, taskId asc` 排序。

### PostgreSQL/MyBatis

新增 Flyway migration：`V3__create_workflow_run_tables.sql`。

```sql
create table workflow_runs (
    id text primary key check (btrim(id) <> ''),
    workflow_id text not null references workflows(id) on delete restrict,
    status text not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    triggered_at timestamptz not null
);

create table task_runs (
    id text primary key check (btrim(id) <> ''),
    workflow_run_id text not null references workflow_runs(id) on delete cascade,
    task_id text not null check (btrim(task_id) <> ''),
    task_name text not null check (btrim(task_name) <> ''),
    status text not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    position integer not null check (position >= 0),
    created_at timestamptz not null,
    unique (workflow_run_id, task_id),
    unique (workflow_run_id, position)
);

create index idx_workflow_runs_workflow_triggered_at_id on workflow_runs(workflow_id, triggered_at, id);
create index idx_task_runs_workflow_run_position on task_runs(workflow_run_id, position);
create index idx_task_runs_workflow_run_status on task_runs(workflow_run_id, status);
```

新增 MyBatis components：

- `WorkflowRunRow`
- `TaskRunRow`
- `WorkflowRunMapper`
- `WorkflowRunMapper.xml`
- `MyBatisWorkflowRunRepository`

`save` 使用事务插入 run 与 task runs。MVP 中 Run 是 append-only，不需要 upsert。

## Bootstrap 设计

建议新增 `WorkflowRunAssemblyConfiguration`，避免 `WorkflowAssemblyConfiguration` 继续膨胀。

默认 profile：

- `InMemoryWorkflowRunRepository`
- `UuidWorkflowRunIdGenerator`
- `UuidTaskRunIdGenerator`

所有 profile：

- `TriggerWorkflowRunUseCase`
- `QueryWorkflowRunUseCase`

`postgres` profile：

- MyBatis adapter 提供 `WorkflowRunRepository` bean。

## 测试策略

### Domain tests

- id value objects trim/reject blank。
- `WorkflowRun.create` 对 `ACTIVE` Workflow 成功并生成 `PENDING`。
- `DRAFT`、`PAUSED`、`ARCHIVED` Workflow 触发失败。
- `TaskRun.create` 生成 `PENDING`。
- TaskRun 拒绝负 position。

### Application tests

- ACTIVE Workflow 触发后保存 1 个 run 和 N 个 task runs。
- task run 顺序与 Workflow DAG tasks 顺序一致。
- workflow 不存在抛 `WorkflowNotFoundException`。
- 非 ACTIVE Workflow 抛 `WorkflowRunTriggerException`，不保存。
- 查询 run 不存在抛 `WorkflowRunNotFoundException`。
- list limit <= 0 抛 `IllegalArgumentException`。

### Adapter tests

- Memory repository 保存/查询 run。
- Memory list 排序和 limit。
- Memory task run 查询排序。
- MyBatis repository Testcontainers 覆盖 save/find/list/task-runs。
- Docker 不可用时明确报告 skipped。

### Web tests

- trigger 成功返回 `201`、`Location`、id。
- trigger missing workflow 返回 `404`。
- trigger non-active workflow 返回 `409`。
- list workflow runs 成功。
- get run 成功 / missing 返回 `404`。
- get task runs 成功 / missing run 返回 `404`。
- limit 越界返回 `400`。

### Bootstrap integration tests

Memory profile：

1. create workflow。
2. activate workflow。
3. trigger run。
4. get run。
5. get task runs。
6. list workflow runs。

Postgres profile 同样覆盖上述路径，但可因 Docker 不可用跳过并报告。

## 验证命令

实现后运行：

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
mvn -f backend/pom.xml test
```

如 Docker 不可用，补充运行非容器测试：

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=!PostgresWorkflowRunApiIntegrationTest
```

## 风险与缓解

### 风险：Run 与 Workflow 定义边界混淆

缓解：Workflow definition 仍在 `domain.workflow`，run 相关类型放入 `domain.execution`。

### 风险：历史 Run 受未来 Workflow 定义变更影响

缓解：Task Run 保存 task id/name 快照。

### 风险：过早实现执行状态推进

缓解：MVP 只创建 `PENDING`，不提供 status update API。

### 风险：MyBatis 空 task list 批量插入生成非法 SQL

缓解：Workflow DAG 当前必须至少一个 task；repository 仍可在插入 task runs 前防御性判断空列表。

### 风险：未来按 status 查询性能不足

缓解：本 slice 不按 status 查询；只为 task run status 加 run 内状态索引，workflow run status 全局索引留到 scheduler/query 需求出现时再加。

## 接受标准

1. 只有 `ACTIVE` Workflow 可以触发 Run。
2. 触发成功返回 `201 Created` 和 Run id。
3. 新建 Workflow Run 状态为 `PENDING`。
4. 每个 Workflow task 都生成一个 `PENDING` Task Run。
5. 可查询单个 Run。
6. 可查询 Workflow 的 Run 列表。
7. 可查询 Run 的 Task Run 列表。
8. Memory 和 MyBatis repository 都保存并还原 run/task-run。
9. 非 ACTIVE 触发返回 `409 Conflict`。
10. 所有非容器测试通过；容器测试如因 Docker 不可用跳过，必须明确报告。

## 自检

- 无 `TBD`、`TODO` 或留空章节。
- 本 slice 不包含 scheduler/worker/run 状态推进。
- Domain/Application 保持 framework-free。
- REST API 不引入 envelope。
- Persistence schema 使用 V3，不修改已发布 V1/V2。
