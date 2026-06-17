# ADR 0004: Worker 核心框架中立化

## 状态

已接受

## 背景

GraphPilot 的 worker 负责 DAG 任务执行，通过事件驱动（`WorkflowRunCreatedEvent`）触发。最初所有 worker 代码——task handlers（http/shell/mock）、handler registry、Spring 事件监听器与 `EventPublisherPort` 的 Spring 实现——都集中在 `graphpilot-adapter-worker-spring` 单一模块中。

入站契约 `ExecuteWorkflowRunUseCase` 与出站端口 `EventPublisherPort` 本身已经是框架中立的，Spring 只充当事件总线的适配器。但 task handlers 与 registry 这些纯逻辑与 Spring 共享同一模块与 pom，没有任何强制约束阻止它们被 Spring 注解污染，也无法在脱离 Spring 的情况下被复用。

未来 worker 可能需要运行在 Micronaut 上，或作为独立进程（如 Kubernetes worker pod、轮询 `findByStatus(PENDING)` 的 standalone runtime）部署。若核心逻辑与 Spring 耦合，这些目标都需要重写。

## 决策

将 worker 的框架中立核心从 Spring 适配器中拆分为独立 Maven 模块：

- 新增 `graphpilot-adapter-worker`（框架中立核心）：包含 `TaskHandlerRegistry` 与 `http`/`shell`/`mock` handlers，包名 `com.graphpilot.adapter.worker.handler`，仅依赖 `graphpilot-application`（传递依赖 `graphpilot-domain`），**不依赖任何运行时框架**。
- `graphpilot-adapter-worker-spring` 瘦身为 Spring 事件胶水：仅保留 `SpringEventPublisher`（实现 `EventPublisherPort`，向 Spring 事件总线发布）与 `WorkflowRunEventListener`（订阅事件并调用 `ExecuteWorkflowRunUseCase.execute()`），依赖新的 worker 核心模块，不承载任何 handler 逻辑。

职责边界：

```text
graphpilot-adapter-worker        (framework-free: handlers + registry)
        ↑ depends on
graphpilot-adapter-worker-spring (Spring glue: event publisher + listener)
        ↑ depends on
graphpilot-bootstrap-spring      (wires glue + core into runtime)
```

## 影响

- Worker 核心逻辑可在脱离 Spring 的情况下测试与复用；`dependency:tree` 实测仅含 application/domain + junit/assertj。
- 在 Micronaut 上托管 worker 的可行性已由 `graphpilot-adapter-worker-micronaut` PoC 验证：新增一个 Micronaut 事件胶水模块（`MicronautEventPublisher` 实现 `EventPublisherPort` + `WorkflowRunEventListener` 实现 `ApplicationEventListener`），核心 handlers/registry 与 `ExecuteWorkflowRunUseCase` 直接复用。该 PoC 仅编译期校验与单元测试，未接入完整 Micronaut runtime。
- 跨进程部署时，出站端口 `EventPublisherPort` 已抽象发布侧；订阅侧可由独立 runtime 轮询 `findByStatus(PENDING)` 驱动，无需走 Spring 事件总线。
- 多了 Maven 模块（worker 核心与可选的 Micronaut 胶水），reactor 构建步骤增加，但模块边界强化了 ADR 0003 的依赖方向约束。
- Spring 适配器失去对 handler 实现的本地引用，bootstrap 通过依赖 worker 核心模块间接获得 handler 类，装配点集中在 `WorkerAssemblyConfiguration`。
- Micronaut 事件总线要求事件载荷继承 `ApplicationEvent`，而领域 `WorkflowRunCreatedEvent` 必须保持框架中立，故胶水层引入 `WorkflowRunCreatedApplicationEvent` 包装器在适配边界转换。
