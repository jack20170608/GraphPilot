# Workflow Lifecycle/Status 设计

日期：2026-06-14

## 背景

GraphPilot 当前已经完成 Workflow 定义的早期纵向切片：

- `POST /api/workflows` 创建 Workflow。
- `GET /api/workflows/{id}` 查询单个 Workflow。
- `GET /api/workflows?limit=50` 查询 Workflow 列表。
- 默认 profile 使用 in-memory persistence adapter。
- `postgres` profile 使用 PostgreSQL、Flyway 和 MyBatis 持久化 Workflow 定义。

现有 Workflow 只有定义数据，没有生命周期状态。后续 scheduler、execution runtime、暂停恢复和归档能力都需要明确区分哪些 Workflow 可以被调度，哪些只是草稿，哪些被暂停或归档。因此本 slice 新增 Workflow lifecycle/status 作为 execution/scheduler 之前的基础能力。

## 目标

1. 为 Workflow 增加状态字段：`DRAFT`、`ACTIVE`、`PAUSED`、`ARCHIVED`。
2. 创建 Workflow 时默认状态为 `DRAFT`。
3. 支持最小状态机转换：
   - `DRAFT -> ACTIVE`
   - `ACTIVE -> PAUSED`
   - `PAUSED -> ACTIVE`
   - `ACTIVE -> ARCHIVED`
   - `PAUSED -> ARCHIVED`
4. 非法状态转换返回 HTTP `409 Conflict`。
5. `GET`、`LIST` 和 lifecycle action 响应都包含 `status`。
6. in-memory 和 PostgreSQL/MyBatis persistence adapters 都持久化 `status`。
7. 保持 Hexagonal Architecture 边界：状态机规则在 domain/application 内，web 和 persistence adapters 不承载业务规则。

## 非目标

本 slice 不实现以下能力：

- Workflow run 或 task run。
- Scheduler 或 worker execution。
- Cron/trigger 配置。
- Workflow definition update/delete。
- 从 `ARCHIVED` 恢复。
- API response envelope。
- 状态变更审计表、操作人记录或事件发布。
- 并发版本号、乐观锁或分布式锁。

## 状态模型

新增 `WorkflowStatus`：

```java
public enum WorkflowStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ARCHIVED
}
```

状态含义：

- `DRAFT`：创建后的默认状态。表示 Workflow 定义已保存，但未进入调度候选范围。
- `ACTIVE`：已激活。后续 scheduler/execution 只应考虑该状态的 Workflow。
- `PAUSED`：暂停。定义仍保留，但后续 scheduler 不应继续触发新 run。
- `ARCHIVED`：归档终态。第一版不支持恢复。

允许转换：

```text
DRAFT  --activate-->  ACTIVE
ACTIVE --pause----->  PAUSED
PAUSED --resume---->  ACTIVE
ACTIVE --archive--->  ARCHIVED
PAUSED --archive--->  ARCHIVED
```

不允许转换示例：

- `DRAFT -> PAUSED`
- `DRAFT -> ARCHIVED`
- `PAUSED -> DRAFT`
- `ARCHIVED -> ACTIVE`
- `ARCHIVED -> PAUSED`
- `ARCHIVED -> DRAFT`

## Domain 设计

`Workflow` record 增加 `WorkflowStatus status` 字段。

建议保留当前不可变风格，状态转换方法返回新的 `Workflow` 实例，而不是修改当前对象：

```java
Workflow activate();
Workflow pause();
Workflow resume();
Workflow archive();
```

创建方法默认使用 `DRAFT`：

```java
Workflow.create(id, name, dag, createdAt)
```

如果需要从 persistence 还原历史状态，可增加显式工厂：

```java
Workflow.restore(id, name, dag, status, createdAt)
```

`restore` 只用于从可信持久化数据还原完整 aggregate，不应绕过空值校验。

非法转换抛出 domain exception，例如 `WorkflowLifecycleException`。错误消息应稳定、清晰，便于 web adapter 映射为 `409 Conflict`：

```text
Cannot pause workflow from status DRAFT
Cannot activate workflow from status ARCHIVED
```

## Application 设计

新增 inbound port：

```java
public interface ChangeWorkflowLifecycleUseCase {
    Workflow activate(WorkflowId workflowId);
    Workflow pause(WorkflowId workflowId);
    Workflow resume(WorkflowId workflowId);
    Workflow archive(WorkflowId workflowId);
}
```

新增 application service：`ChangeWorkflowLifecycleService`。

每个方法执行相同流程：

1. 校验 `workflowId` 非空。
2. 调用 `WorkflowRepository.findById(workflowId)`。
3. 不存在时抛出 application-level not found exception，沿用当前 query service 的 404 映射方式。
4. 调用对应 domain lifecycle 方法。
5. 调用 `WorkflowRepository.save(updatedWorkflow)`。
6. 返回保存后的 `Workflow`。

Application 层不依赖 Spring、MyBatis、HTTP DTO 或 persistence row 类型。

## REST API 设计

新增动作端点：

```http
POST /api/workflows/{id}/activate
POST /api/workflows/{id}/pause
POST /api/workflows/{id}/resume
POST /api/workflows/{id}/archive
```

请求 body 为空。成功响应：`200 OK`，body 为完整 `WorkflowResponse`。

示例响应：

```json
{
  "id": "workflow-id",
  "name": "Daily ETL",
  "status": "ACTIVE",
  "createdAt": "2026-06-14T00:00:00Z",
  "tasks": [
    { "id": "extract", "name": "Extract data" }
  ],
  "edges": []
}
```

现有响应同步增加 `status`：

- `GET /api/workflows/{id}`
- `GET /api/workflows?limit=50`
- lifecycle action endpoints

`POST /api/workflows` 可以继续只返回 `{ "id": "..." }`，不在本 slice 改变 create response 合约；创建后通过 get/list 可看到 `DRAFT`。

## 错误处理

- Workflow 不存在：`404 Not Found`。
- 非法状态转换：`409 Conflict`。
- path id 为空或非法：沿用 `WorkflowId` validation 和当前 bad request 处理。
- persistence/database 错误：不新增 API envelope，不在本 slice 改全局错误模型。

建议新增 web exception handler 分支，将 `WorkflowLifecycleException` 映射为：

```http
HTTP/1.1 409 Conflict
Content-Type: text/plain;charset=UTF-8

Cannot pause workflow from status DRAFT
```

如果当前项目已有统一 error response 类型，则应复用；当前 slice 不引入新的 envelope。

## Persistence 设计

### In-memory adapter

`InMemoryWorkflowRepository` 当前保存完整 `Workflow` 对象。`Workflow` 增加 `status` 后，只要 repository 保持 immutable/copy-on-write 行为，即可自然保存状态。

### MyBatis/PostgreSQL adapter

因为当前 `V1__create_workflow_tables.sql` 仍处于早期开发阶段并刚刚引入，尚无生产迁移兼容要求，本 slice 直接更新 V1 schema。

`workflows` 表增加：

```sql
status text not null check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED'))
```

`WorkflowRow` 增加 `status` 字段。Mapper 的 insert/upsert/select result map 都同步包含 `status`：

```sql
insert into workflows (id, name, status, created_at)
values (#{id}, #{name}, #{status}, #{createdAt})
on conflict (id) do update set
    name = excluded.name,
    status = excluded.status,
    created_at = excluded.created_at
```

Domain 映射时使用 `WorkflowStatus.valueOf(row.status())` 或让 `WorkflowRow` 直接持有 `WorkflowStatus`。优先推荐 row 使用 `String status`，adapter 显式转换为 domain enum，避免 MyBatis enum 映射配置成为隐藏依赖。

## Bootstrap 设计

`WorkflowAssemblyConfiguration` 增加 bean：

```java
@Bean
ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase(WorkflowRepository workflowRepository) {
    return new ChangeWorkflowLifecycleService(workflowRepository);
}
```

默认 profile 和 `postgres` profile 都复用同一个 application service。repository 的具体实现仍由 profile 决定。

## 数据流

以 activate 为例：

1. `WorkflowController.activate(id)` 解码 path id。
2. 调用 `ChangeWorkflowLifecycleUseCase.activate(WorkflowId)`。
3. `ChangeWorkflowLifecycleService` 从 `WorkflowRepository` 读取 Workflow。
4. Domain `Workflow.activate()` 校验 `DRAFT -> ACTIVE`。
5. service 保存更新后的 Workflow。
6. controller 将返回的 Workflow 映射为 `WorkflowResponse`。

pause、resume、archive 同理。

## 测试策略

### Domain tests

覆盖：

1. 新建 Workflow 默认是 `DRAFT`。
2. `DRAFT -> ACTIVE` 成功。
3. `ACTIVE -> PAUSED` 成功。
4. `PAUSED -> ACTIVE` 成功。
5. `ACTIVE -> ARCHIVED` 成功。
6. `PAUSED -> ARCHIVED` 成功。
7. 非法转换抛出 `WorkflowLifecycleException`。
8. 状态转换返回新实例，原实例状态不变。

### Application tests

新增 `ChangeWorkflowLifecycleServiceTest`，覆盖：

1. activate 找到 Workflow、转换并保存。
2. pause/resume/archive 的 happy path。
3. missing workflow 抛出 not found exception。
4. 非法转换异常从 domain 传播，不被静默吞掉。

### Web adapter tests

更新 `WorkflowControllerTest`，覆盖：

1. 四个 lifecycle action endpoint 成功返回 `200 OK`。
2. 响应 body 包含 `status`。
3. missing workflow 返回 `404 Not Found`。
4. 非法转换返回 `409 Conflict`。
5. get/list response 包含 `status`。

### Persistence adapter tests

更新：

- `InMemoryWorkflowRepositoryTest`：save/find/list 保留 status。
- `MyBatisWorkflowRepositoryTest`：save/find/list 保留 status；转换后保存可覆盖旧状态。

### Bootstrap tests

更新：

- 默认 profile integration test：create 后 get/list 返回 `DRAFT`。
- postgres profile integration test：create 后 activate，再 get/list 验证 `ACTIVE`。

Docker 不可用时，Testcontainers 测试继续按当前 `disabledWithoutDocker = true` 策略跳过；非容器测试必须通过。

## 验证命令

实现后至少运行：

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
mvn -f backend/pom.xml test
```

如果 Docker 不可用，应明确报告 Testcontainers skipped，并补充运行非容器验证：

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=!PostgresWorkflowApiIntegrationTest
```

## 风险与缓解

### 风险：状态规则泄漏到 web adapter

缓解：controller 只调用 use case；状态转换规则只在 domain `Workflow` 内表达。

### 风险：旧 persistence row 无 status

缓解：当前迁移尚未发布到生产环境，直接更新 V1。若后续已有生产数据，再改为新增 V2 migration。

### 风险：非法转换错误被映射成 500

缓解：web adapter 增加明确 exception handler，将 `WorkflowLifecycleException` 映射为 `409 Conflict`。

### 风险：未来 scheduler 需要更多状态

缓解：本 slice 保持最小状态机；后续 scheduler/run 可在此基础上新增 `RUNNING`、`FAILED` 等 run-level 状态，而不是污染 workflow definition lifecycle。

## 接受标准

1. 创建 Workflow 后，通过 get/list 可看到 `status: "DRAFT"`。
2. `POST /api/workflows/{id}/activate` 可将 `DRAFT` Workflow 变为 `ACTIVE`。
3. `POST /api/workflows/{id}/pause` 可将 `ACTIVE` Workflow 变为 `PAUSED`。
4. `POST /api/workflows/{id}/resume` 可将 `PAUSED` Workflow 变为 `ACTIVE`。
5. `POST /api/workflows/{id}/archive` 可将 `ACTIVE` 或 `PAUSED` Workflow 变为 `ARCHIVED`。
6. 非法转换返回 `409 Conflict`，不会静默成功。
7. Memory 和 MyBatis repository 都能保存并还原 status。
8. Domain/application/web/persistence/bootstrap 相关测试通过；Docker 不可用时 Testcontainers skipped 被明确报告。

## 自检

- 无 `TBD`、`TODO` 或故意留空章节。
- 本设计聚焦 lifecycle/status，不包含 run/execution/scheduler。
- API 形态、状态机和错误码与用户确认一致。
- Hexagonal Architecture 边界与项目规则一致。
