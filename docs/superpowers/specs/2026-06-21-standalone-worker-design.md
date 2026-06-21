# GraphPilot 独立 Worker 进程设计

## 状态

**实现完成**（2026-06-21）

## 实现状态

## 背景

当前 GraphPilot 后端是一个单进程 Spring Boot 应用（`graphpilot-bootstrap-spring`），Web adapter、持久化 adapter、worker 执行能力都装配在同一个 JVM 内。Worker 接收任务有两条路径：

1. 事件驱动：`TriggerWorkflowRunService.trigger` 发布 `WorkflowRunCreatedEvent`，同进程的 `@Async` `WorkflowRunEventListener` 调用 `ExecuteWorkflowRunUseCase.execute(runId)`。
2. 定时补偿：`WorkerScannerScheduler`（`@Scheduled`）扫描 PENDING run 并执行。

执行核心是 `WorkflowExecutionCoordinatorService.executeTask`，它通过 `TaskHandlerProvider` 抽象获取 handler 并调用 `handler.execute(taskRun, taskDef, resolvedConfig.asMap())`。表达式解析、重试退避、状态/时间线/output 持久化都在 `executeTask` 内完成。Handler 实现位于 framework-free 的 `graphpilot-adapter-worker`，当前为 shell/mock（http handler 已移除）。

本设计的目标是让 worker 能作为**独立进程**运行，验证 worker 可从 scheduler 运行时中剥离，为未来的水平扩展/执行隔离预留演进路径。

## 目标与非目标

### 目标

- Worker 能以独立 JVM 进程启动，接收 scheduler 分发的任务并返回执行结果。
- 调度粒度为 **task 级**：scheduler 保留 coordinator（DAG 顺序、依赖解析、重试、持久化），把每个可运行 task 分发给 worker 执行。
- 传输方式先做 **HTTP**（请求/响应语义最匹配 task 执行），同时预留 message queue 演进。
- 复用现有 `TaskHandlerProvider` 抽象，coordinator 代码零改动。
- 保持 `graphpilot-domain` / `graphpilot-application` / `graphpilot-adapter-worker` 的 framework-free 边界不变。
- 现有 `local` 模式（handler 同进程执行）行为不变，作为默认。

### 非目标

- 不做多 worker 实例 / 负载均衡 / worker 间故障转移（PoC 只指向单个 worker URL）。
- 不实现 message queue 传输实现（只预留 port 抽象）。
- worker 进程不做持久化、不做重试、不做 DAG 调度（worker 是无状态执行器）。
- 不做 scheduler 与 worker 之间的鉴权 / TLS（PoC 走 localhost 明文 HTTP）。
- 不改变事件驱动触发路径与 PENDING scanner 的既有行为（scheduler 侧）。

## 架构

### 心智模型

Scheduler（当前 `GraphPilotApplication`）是大脑：持有 DAG coordinator、表达式解析、重试、状态/时间线/output 持久化。每个可运行 task 不再在进程内调 handler，而是通过 HTTP 分发给 **worker 进程**，后者持有真正的 `TaskHandlerRegistry`（shell/mock）并执行，返回 `TaskResult`。Worker 是无状态执行器：task 进 → `TaskResult` 出，无数据库依赖。

关键依据：`executeTask` 已经通过 `TaskHandlerProvider` 抽象消费 handler（`taskHandlerProvider.getHandler(taskRun.taskType())` → `handler.execute(...)`）。因此整改动是一次**可替换的 remote provider**（scheduler 侧）+ 一个 **worker HTTP 端点**（worker 侧）。Coordinator、handlers、domain、persistence 均不改动。

### 数据流

单个 task 的端到端流程：

```
coordinator.executeTask(run, taskDef, t)
  ├─ resolve expressions → resolvedConfig            [scheduler, 既有]
  ├─ handler = remoteTaskHandlerProvider.getHandler(taskType)  → RemoteTaskHandler
  ├─ result = handler.execute(taskRun, taskDef, resolvedConfig.asMap())
  │        └─ POST /api/worker/execute  { taskRun, taskDefinition, config }
  │               worker: registry.getHandler(taskRun.taskType()).execute(...) → TaskResult
  │               worker: map → TaskExecutionResponse (200)
  │        └─ map response → TaskResult
  ├─ persist status/output/timeline, retry/backoff as today   [scheduler, 既有]
```

表达式解析发生在调度侧（`executeTask` 第 203 行），worker 收到的 `config` 是已解析的 map，worker 不感知表达式机制。重试、退避、PENDING 复位、时间线、output 持久化全部不变，全在 scheduler 侧。Worker 是纯函数。

### 模块结构

新增 2 个模块：

1. **`graphpilot-adapter-worker-http`**（新增）—— task 分发的 HTTP 传输 adapter，包含双向 + 共享 DTO：
   - 入站（worker 侧）：`WorkerTaskController` —— `POST /api/worker/execute`，反序列化请求，路由到本地 `TaskHandlerRegistry`，把 `TaskResult` 映射为响应。
   - 出站（scheduler 侧）：`RemoteTaskHandlerProvider implements TaskHandlerProvider` + `RemoteTaskHandler` —— 序列化调用，POST 到 worker URL，把响应映射为 `TaskResult`。
   - 共享契约：`TaskExecutionRequest`、`TaskExecutionResponse` records + 映射器。
   - 依赖：`graphpilot-application` + `graphpilot-adapter-worker` + `graphpilot-domain`；使用 Spring（web + HTTP client）。

2. **`graphpilot-bootstrap-worker`**（新增）—— Spring Boot worker 进程：`WorkerApplication` main，装配 `WorkerTaskController` + `TaskHandlerRegistry`（shell/mock）+ `JacksonJsonValueCodec`。无 web/workflow controller，无持久化，无 scanner。依赖 `adapter-worker-http` + `adapter-worker` + `application` + `domain`。

既有模块改动：

- **`graphpilot-bootstrap-spring`** —— 新增分发配置：`graphpilot.worker.dispatch.mode=local|remote`（默认 `local`，行为不变）。`remote` 模式下，用 `adapter-worker-http` 的 `RemoteTaskHandlerProvider` 作为 `TaskHandlerProvider` bean，替代 `TaskHandlerRegistry`。
- **`backend/pom.xml`** —— 注册 2 个新模块。

### 契约

请求 DTO（scheduler → worker），直接内嵌共享的 domain records（类型安全，不字段镜像）：

```java
public record TaskExecutionRequest(TaskRun taskRun, TaskDefinition taskDefinition, Map<String, Object> config) {}
```

`config` 是已表达式解析的 map。Worker 不接触表达式解析器。

响应 DTO（worker → scheduler）—— `TaskResult` 构造器私有，故用普通 DTO：

```java
public record TaskExecutionResponse(String status, String output, String error, String errorMessage) {}
```

Worker 通过 `TaskResult` 访问器映射为 response；scheduler 通过 `TaskRunStatus.valueOf(status)` → `TaskResult.success(output)` / `failure(error, errorMessage)` / `skipped()` 映射为 `TaskResult`。

### `RemoteTaskHandlerProvider` 抽象与 MQ 预留

`TaskHandlerProvider` 是 application 层 framework-free 接口，已有两个实现：本地 `TaskHandlerRegistry`（adapter-worker）和即将新增的 `RemoteTaskHandlerProvider`（adapter-worker-http，HTTP）。本设计不新增更高层的分发 port —— `TaskHandlerProvider` 本身就是分发抽象。未来 MQ 传输只需再增一个 `TaskHandlerProvider` 实现（如 `MessageQueueTaskHandlerProvider`），coordinator 仍零改动。这是预留 MQ 的方式：不提前引入抽象，只保证既有抽象足以承载新实现。

`RemoteTaskHandlerProvider.getHandler(taskType)` 对任意 `taskType` 返回同一个 `RemoteTaskHandler` 实例（task type 路由发生在 worker 侧，不在 scheduler 侧）。`getAllHandlers()` 在 scheduler 侧无人调用（仅 registry 测试使用），返回空列表即可。

### Worker 状态lessness

Worker 进程不依赖任何持久化 adapter、不依赖 `WorkflowRunRepository`、不运行 scanner、不发布/监听 `WorkflowRunCreatedEvent`。它只暴露一个 HTTP 端点，按请求执行一个 task 并返回结果。所有领域状态（run/task 状态、output、时间线）由 scheduler 持有与持久化。

## 错误处理

Worker 失败有两种方式，scheduler 各自映射到 `executeTask` 既有重试/恢复路径：

| 失败场景 | Worker 行为 | Scheduler 处理 |
|---|---|---|
| Task handler 失败（shell 非零退出、mock 失败、抛异常） | 返回 `TaskExecutionResponse`，FAILED 状态 + error/errorMessage（200 OK）。既有 handler 已返回 `TaskResult.failure(...)` 而非抛异常。 | 正常失败路径 → `canRetry()` 则重试，否则终态 FAILED。**不变。** |
| HTTP 传输失败（超时、连接拒绝、5xx、反序列化失败） | 不适用 —— 网络层。`RemoteTaskHandler.execute` 捕获并返回 `TaskResult.failure("REMOTE_UNAVAILABLE", msg)`。 | 同 handler 失败 → 重试路径。可扛住 worker 崩溃/重启。 |
| Worker 上未知 task type | Worker 返回 400 + error。`RemoteTaskHandler` 映射为 `TaskResult.failure("UNKNOWN_TYPE", ...)`。 | → 重试/终态路径。 |
| 响应含未知 status 字符串 | `RemoteTaskHandler` → `TaskResult.failure("BAD_RESPONSE", ...)`。 | → 重试/终态路径。 |

设计原则：**所有 worker 失败都呈现为普通 `TaskResult.failure`**。Coordinator 无需新增任何错误处理分支 —— 它已用 try/catch 包裹 `handler.execute` 并投影为 `TaskResult`。Worker 中途崩溃与 handler 抛异常在 scheduler 看来完全相同，重试照常触发。无卡死 task，无新增部分状态清理。

### 超时与重复执行

HTTP 超时必须 > 最长 task 执行时间，否则仍在跑的 task 会被标 FAILED 而此刻 worker 还在执行（重试时重复执行）。默认 handler 超时：shell ≤60s，mock ≤100ms。设 HTTP client 超时为安全上限（默认 90s），可配置。Shell 自身的 `timeout` config 仍约束真实执行时长。

注意：当前 PoC 不做 worker 侧幂等/任务认领，因此超时触发的重试确实可能产生重复执行。这对 shell/http 这类带副作用 task 是已知风险，但属于非目标范围（多 worker / 幂等在后续 slice 处理）。本设计仅在文档中明确该风险，不引入额外机制。

## 测试

三层，各自独立且快（除集成冒烟外无真实网络）：

1. **Unit —— `RemoteTaskHandlerProvider` / `RemoteTaskHandler`**：用 OkHttp `MockWebServer` 桩 `/api/worker/execute`。断言：请求序列化正确；200-success → `TaskResult.success`；200-failure → `TaskResult.failure`；timeout/5xx → `TaskResult.failure("REMOTE_UNAVAILABLE",...)`。位于 `adapter-worker-http` test sources。
2. **Unit —— `WorkerTaskController`**：Spring `@WebMvcTest`（或 MockMvc standalone）。桩一个返回 fake handler 的 `TaskHandlerProvider`；断言端点按 `taskRun.taskType()` 路由，正确映射 `TaskResult` → response，未知 type → 400。无需真实 handler。
3. **Integration —— coordinator + remote provider**：扩展现有 `WorkflowExecutionCoordinatorServiceTest` 模式。用 `RemoteTaskHandlerProvider` 对接 in-process `MockWebServer`（模拟 worker），驱动一个小 DAG 穿过 coordinator，断言状态/时间线/传输失败时重试。证明 scheduler 侧端到端可用，使用 fake worker。
4. **E2E 冒烟（手动 / `scripts/`）**：启动真实 `bootstrap-worker`（HTTP 端口）+ `remote` 模式的 `bootstrap-spring`（in-memory 持久化），创建+激活含 shell/mock task 的 workflow，触发 run，断言 SUCCEEDED + outputs。这是“先能跑就行”的证明。Docker Compose 可选 —— 两个本地 JVM 足够。

既有 handler 测试、coordinator 测试、web 测试不改 —— `local` 模式为默认且行为一致。

## 配置

Scheduler 侧（`bootstrap-spring`），均在 `graphpilot.worker.dispatch.*`：

```yaml
graphpilot:
  worker:
    dispatch:
      mode: local          # local（默认，handler 同进程）| remote（HTTP）
      remote:
        base-url: http://localhost:8081   # worker HTTP base URL
        timeout-ms: 90000                  # HTTP client 读/连接超时
```

- `mode=local`：今日行为，`TaskHandlerRegistry` 作为 `TaskHandlerProvider`。既有部署零风险。
- `mode=remote`：装配 `RemoteTaskHandlerProvider`，指向单个 worker URL。多 worker / 负载均衡显式属非目标。

Worker 侧（`bootstrap-worker`）：

```yaml
server:
  port: 8081
graphpilot:
  worker:
    handlers: [shell, mock]   # 注册哪些 handler（默认两者）
```

配置极简 —— 它是无状态 HTTP 执行器。

### Scheduler / worker 职责边界

- Scheduler（`bootstrap-spring` in `remote` mode）：保留 coordinator、表达式解析、重试、持久化、事件触发。`remote` 模式下不再在进程内持有 handler 实现。PENDING scanner 可保持开启（它会通过 remote provider 分发），或关闭后仅由事件路径驱动 —— 二者皆可，PoC 选择保持 scanner 开启以兼容既有补偿行为。
- Worker（`bootstrap-worker`）：只暴露 HTTP 端点，按请求执行 task 并返回结果。不跑 scanner，不发布/监听 domain event，不访问 DB。

## 演进路径（非本设计实现）

- 多 worker / 负载均衡 / 故障转移：在 `RemoteTaskHandlerProvider` 之上增加 worker 列表 + 选择策略，或前置 LB。
- Message queue 传输：新增 `MessageQueueTaskHandlerProvider implements TaskHandlerProvider`，用 correlation id + reply queue 实现请求/响应；coordinator 零改动。
- Worker 侧幂等/任务认领：解决超时重试导致的重复执行，需 worker 持久化执行记录或引入 idempotency key。
- 鉴权 / TLS：scheduler↔worker 之间加 mTLS 或共享 token。

## 相关文档

- [架构概览](../../../docs/architecture/overview.md)
- [ADR 0004 Framework-free Worker Core](../../../docs/architecture/adr/0004-framework-free-worker-core.md)
- [Worker 执行引擎设计](./2026-06-14-worker-execution-engine-design.md)
- [Task Config Expressions 设计](./2026-06-18-graphpilot-task-config-expressions-design.md)
