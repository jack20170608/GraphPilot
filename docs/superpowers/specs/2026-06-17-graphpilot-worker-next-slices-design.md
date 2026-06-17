# GraphPilot Worker 下一阶段能力设计

## 状态

已批准进入计划阶段。

## 背景

GraphPilot 当前已经具备：

- 工作流定义、生命周期、运行触发与查询 API。
- Worker 可按 DAG 拓扑执行任务，并支持重试、退避、输出持久化。
- Spring 与 Micronaut worker glue 均复用框架中立 worker core。
- 前端 run detail 能轮询状态并实时着色 DAG。

下一阶段目标是把 worker 从“可执行”升级为“可配置、可恢复、可观测、双 runtime API 一致”。本设计覆盖四项连续里程碑能力：

1. `TaskDefinition.input/config` 静态 JSON 建模。
2. worker 扫描/保守恢复机制。
3. 结构化任务 timeline。
4. Micronaut runtime API 对齐 Spring API。

## 目标与非目标

### 目标

- 每个 task 可携带静态 JSON config，handler 从 task config 获取输入。
- Worker 不只依赖创建事件，也可扫描 `PENDING` workflow runs 并执行。
- Run detail 能通过结构化 timeline 展示运行过程。
- Spring 与 Micronaut 暴露同路径、同语义、同字段的 REST API。
- 保持 Hexagonal Architecture：domain/application 不依赖 Spring、Micronaut、MyBatis 或前端。

### 非目标

- 不做上游 output 引用，例如 `extract.output`。
- 不做表达式/模板系统，例如 `${tasks.extract.output}`。
- 不自动重置或失败化长期卡住的 `RUNNING` run/task。
- 不做 stdout/stderr/http body 的完整日志流。
- 不抽共享 web-contract 模块；Spring/Micronaut DTO 第一版允许重复。

## 总体架构

```text
domain
  TaskConfig
  TaskDefinition(config)
  WorkflowRunTimelineEvent / TimelineEventType

application
  Trigger / Execute / Query / Scan use cases
  ports: WorkflowRunRepository, TimelineRepository, Clock, IDs

adapters
  persistence-memory / persistence-mybatis
  worker-core handlers
  web-spring
  worker-spring glue
  worker-micronaut glue

bootstraps
  bootstrap-spring
  bootstrap-micronaut
```

规则：

- `graphpilot-domain` 与 `graphpilot-application` 继续保持 framework-free。
- `TaskConfig` 作为薄 value object 包装不可变 JSON map。
- Timeline 事件为 append-only 结构化事件，不承载长日志内容。
- Scanner 位于 application 层；Spring/Micronaut 只负责调度触发。

## TaskDefinition 静态 Config

### Domain 模型

新增 `TaskConfig`：

```java
public record TaskConfig(Map<String, Object> values) {
    public static TaskConfig empty();
    public Object get(String key);
    public Optional<String> getString(String key);
    public Optional<Long> getLong(String key);
    public Map<String, Object> asMap();
}
```

`TaskDefinition` 调整为：

```java
public record TaskDefinition(
    TaskId id,
    String name,
    String type,
    TaskConfig config
)
```

兼容构造：

- `new TaskDefinition(id, name)` → `type=mock`, `config={}`。
- `new TaskDefinition(id, name, type)` → `config={}`。
- `TaskDefinition.of(id, name, type, config)`。

规则：

- `config` 可省略，默认空对象。
- `TaskConfig` 对输入 map 做 defensive copy。
- `TaskConfig` 不解释表达式，也不引用上游输出。

### Handler 输入

`WorkflowExecutionCoordinatorService` 改为：

```java
handler.execute(taskRun, taskDefinition, taskDefinition.config().asMap())
```

不再传 `Map.of()`。

Handler 读取规则：

- `shell`
  - `command` 必填
  - `timeout` 可选
  - `workingDir` 可选
- `http`
  - `url` 必填
  - `method` 默认 `GET`
  - `headers` 可选
  - `body` 可选
- `mock`
  - `delayMs` 可选
  - `success` 可选，用于确定性测试/演示
- `poc`
  - Micronaut bootstrap PoC 的确定性 handler，可忽略 config。

### Persistence

PostgreSQL/MyBatis：

```sql
ALTER TABLE workflow_tasks
  ADD COLUMN config jsonb NOT NULL DEFAULT '{}';
```

实现策略：

- MyBatis row 使用 `String configJson` 或 `Map<String, Object>`，优先用 String JSON 以减少 type handler 复杂度。
- mapper insert/select 包含 `config`。
- domain 映射时 parse/stringify config。

In-memory persistence 无额外 schema。

### REST API

Spring 与 Micronaut create/get/list workflow DTO 同步包含 `config`：

```json
{
  "name": "ETL",
  "tasks": [
    {
      "id": "extract",
      "name": "Extract",
      "type": "http",
      "config": {
        "url": "https://example.com/data",
        "method": "GET"
      }
    },
    {
      "id": "load",
      "name": "Load",
      "type": "shell",
      "config": {
        "command": "echo done",
        "timeout": 10
      }
    }
  ],
  "edges": [
    { "fromTaskId": "extract", "toTaskId": "load" }
  ]
}
```

## Worker 扫描与保守恢复

### Use Case

新增：

```java
public interface ScanPendingWorkflowRunsUseCase {
    ScanResult scan(int limit);
}
```

`ScanResult` 包含：

- `scannedCount`
- `executedCount`
- `failedCount`
- `failures`：runId + safe message

流程：

1. 调用 `WorkflowRunRepository.findByStatus(PENDING, limit)`。
2. 对每个 run 调用 `ExecuteWorkflowRunUseCase.execute(runId)`。
3. 单个 run 失败不影响后续 run。
4. 返回扫描统计。

### 保守恢复策略

第一版只扫描 `PENDING` runs：

- 不自动重置 `RUNNING` run/task。
- 不把超时 `RUNNING` 自动标记为 `FAILED`。
- 卡住的 `RUNNING` 通过 API/UI 可观测。
- 未来如需恢复卡住 run，新增独立 `RecoverStuckRunsUseCase`。

该策略避免重复执行带副作用的 http/shell task。

### Runtime 调度

Spring bootstrap：

```properties
graphpilot.worker.scanner.enabled=true
graphpilot.worker.scanner.interval-seconds=10
graphpilot.worker.scanner.limit=20
```

Micronaut bootstrap 使用相同配置键。

默认建议：

- `enabled=true`
- `interval-seconds=10`
- `limit=20`

由于只处理 `PENDING`，默认启用风险较低。

### Repository 修正

MyBatis 当前 `findByStatus` 为占位实现，需要改为真实查询：

```sql
select id, workflow_id, status, triggered_at, started_at, finished_at
from workflow_runs
where status = #{status}
order by triggered_at, id
limit #{limit}
```

In-memory 现有实现可保留并补充测试。

## 结构化 Timeline

### Domain 模型

新增：

```java
public record WorkflowRunTimelineEvent(
    TimelineEventId id,
    WorkflowRunId workflowRunId,
    TaskRunId taskRunId,
    TaskId taskId,
    TimelineEventType type,
    String message,
    Instant occurredAt
)
```

`taskRunId` 与 `taskId` 对 run-level 事件为 null。

事件类型：

```java
RUN_CREATED
RUN_STARTED
TASK_STARTED
TASK_SUCCEEDED
TASK_FAILED
TASK_SKIPPED
RUN_SUCCEEDED
RUN_FAILED
```

规则：

- Timeline 事件 append-only。
- `message` 为短文本，可直接展示。
- 不记录完整 stdout/stderr/http body。
- handler output 继续保存在 `TaskRun.output`。

### Application Ports

```java
public interface WorkflowRunTimelineRepository {
    WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event);
    List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId runId, int limit);
}
```

```java
public interface TimelineEventIdGeneratorPort {
    TimelineEventId nextTimelineEventId();
}
```

写入点：

- `TriggerWorkflowRunService` 创建 run 后写 `RUN_CREATED`。
- `WorkflowExecutionCoordinatorService`：
  - run 转 RUNNING 前写 `RUN_STARTED`。
  - task 转 RUNNING 前写 `TASK_STARTED`。
  - task 成功/失败/跳过后写对应事件。
  - run 成功/失败后写 `RUN_SUCCEEDED/RUN_FAILED`。

### Persistence

PostgreSQL/MyBatis：

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

In-memory：

- `ConcurrentMap<WorkflowRunId, List<WorkflowRunTimelineEvent>>`
- append on save
- query sorted by `occurredAt, id`

### REST API

Spring 与 Micronaut 都新增：

```http
GET /api/workflow-runs/{runId}/timeline?limit=200
```

Response：

```json
[
  {
    "id": "...",
    "workflowRunId": "...",
    "taskRunId": "...",
    "taskId": "extract",
    "type": "TASK_SUCCEEDED",
    "message": "Task extract succeeded",
    "occurredAt": "2026-06-17T..."
  }
]
```

### Frontend

Run detail 新增 `Timeline` tab：

- 与 `DAG 拓扑`、`任务列表` 并列。
- run 未终态时每 1.5 秒轮询 timeline，终态后停止。
- 事件按 `occurredAt` 升序展示。
- 颜色：
  - started/running 蓝
  - succeeded 绿
  - failed 红
  - skipped 灰
- 每项显示时间、类型、message、taskId（如有）。

## Micronaut API 对齐 Spring

### 对齐范围

Micronaut runtime 对齐当前 Spring API：

```http
POST /api/workflows
GET  /api/workflows?limit=50
GET  /api/workflows/{id}
POST /api/workflows/{id}/activate
POST /api/workflows/{id}/pause
POST /api/workflows/{id}/resume
POST /api/workflows/{id}/archive
GET  /api/workflows/{id}/runs?limit=50
POST /api/workflows/{id}/runs
GET  /api/workflow-runs/{id}
GET  /api/workflow-runs/{id}/tasks
GET  /api/workflow-runs/{id}/timeline?limit=200
```

### DTO 字段

Micronaut records 与 Spring DTO 同构：

- `WorkflowResponse`
  - `id`, `name`, `status`, `tasks`, `edges`, `createdAt`
- `TaskResponse`
  - `id`, `name`, `type`, `config`
- `WorkflowRunResponse`
  - `id`, `workflowId`, `status`, `triggeredAt`, `startedAt`, `finishedAt`
- `TaskRunResponse`
  - `id`, `workflowRunId`, `taskId`, `taskName`, `taskType`, `status`, `position`, `retryCount`, `maxRetries`, `errorMessage`, `output`, `startedAt`, `finishedAt`, `createdAt`
- `TimelineEventResponse`
  - `id`, `workflowRunId`, `taskRunId`, `taskId`, `type`, `message`, `occurredAt`

第一版不抽共享 `web-contract` 模块，避免过早抽象。若重复持续扩大，再新增 `graphpilot-adapter-web-contract`。

### 异常映射

Micronaut 对齐 Spring：

- workflow not found → 404
- workflow run not found → 404
- illegal lifecycle transition → 409
- invalid request/limit → 400
- unexpected → 500

使用 Micronaut `@Error` 或 `ExceptionHandler` 实现。

## 实施顺序

1. Task config 建模与 handler 输入打通。
2. Worker scanner 与 `findByStatus` 修正。
3. Timeline domain/application/persistence/API。
4. Micronaut API 对齐 Spring。
5. Frontend workflow config 输入与 timeline tab。

## 风险与控制

- JSON config 类型安全：第一版只在 handler 边界做必填校验；domain 只保证不可变和非 null。
- MyBatis JSONB 映射：先用 String JSON 存取，避免复杂 type handler。
- handler 副作用：scanner 只处理 PENDING，不碰 RUNNING，避免重复执行。
- timeline 事件重复：当前 scanner 不重复执行 terminal run；未来 RUNNING 恢复再引入幂等 key。
- Spring/Micronaut DTO 重复：短期接受重复，保证 runtime 独立；后续再抽 shared contract。

## 测试计划

### Domain

- `TaskConfig` 默认空、不可变、拒绝 null。
- `TaskDefinition` config 默认与 round-trip。
- `WorkflowRunTimelineEvent` 必填字段和 message 校验。

### Application

- coordinator 将 `task.config` 传给 handler。
- scanner 扫描 PENDING，limit 生效，失败隔离。
- timeline 事件顺序覆盖 run/task start/success/failure/skip。

### Persistence

- MyBatis workflow task config round-trip。
- MyBatis timeline save/list。
- MyBatis `findByStatus(PENDING)` 正确。
- In-memory timeline save/list。

### Web Spring

- workflow config API round-trip。
- timeline endpoint。
- scanner wrapper 或 direct bean 测试。

### Micronaut

- 与 Spring 同路径 API E2E。
- config → handler → output → timeline 全链路。
- lifecycle endpoints 成功/冲突映射。

### Frontend

- TypeScript/lint/build。
- create workflow task config JSON 输入。
- run detail timeline tab。
- DAG/status/timeline polling 联动。

## 交付标准

- shell/http/mock/poc handler 都能从 task config 获取输入。
- 新 run 即使事件丢失，也能被 scanner 后续执行。
- run detail 可展示 timeline。
- Spring 与 Micronaut REST API 对齐当前功能集。
- 后端全量测试与前端构建通过。
