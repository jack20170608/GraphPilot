# GraphPilot 架构概览

GraphPilot 被设计为一个 DAG-first 的任务编排平台，后端采用基于 Hexagonal Architecture 的 Java 架构，前端采用现代化的 Next.js Web Console。

## 系统领域

- DAG 定义与校验
- Scheduler 与 dependency resolver
- Task execution runtime
- Metadata store
- API service
- Web Console
- Observability 与 alerting
- Plugin 与 integration system

## 后端架构

后端采用 Ports and Adapters，使核心模型不绑定特定 Web container、Dependency Injection framework、Persistence layer、Security layer 或 Scheduler implementation。

依赖方向：

```text
bootstrap -> adapters -> application -> domain
```

### Core Modules

- `graphpilot-domain` — 纯 domain model、value objects、domain services、domain events 和 domain exceptions。
- `graphpilot-application` — use cases、inbound ports、outbound ports、commands、queries 和 application services。

### Adapter Modules

- `graphpilot-adapter-web-spring` — Spring Web REST adapter。
- `graphpilot-adapter-persistence-memory` — 面向本地开发和早期纵向切片验证的 in-memory persistence adapter。
- `graphpilot-adapter-persistence-mybatis` — MyBatis persistence adapter。启用 `postgres` profile 时，它通过 PostgreSQL 与 Flyway schema 为 Workflow create/get/list 和 Workflow Run 元数据提供持久化实现。
- `graphpilot-adapter-worker` — 框架中立的 worker 核心：task handlers（http/shell/mock）与 handler registry，仅依赖 `graphpilot-application`，不耦合任何运行时框架，便于未来由 Spring、Micronaut 或独立进程托管。
- `graphpilot-adapter-worker-spring` — Spring 事件胶水：将框架中立的 worker 核心桥接到 Spring 事件总线（`SpringEventPublisher` 发布、`WorkflowRunEventListener` 订阅），不承载任何 handler 逻辑。
- `graphpilot-adapter-worker-micronaut` — Micronaut 事件胶水 PoC：与 Spring 胶水对等，复用同一份框架中立 worker 核心（`MicronautEventPublisher` + `WorkflowRunEventListener`），验证 worker 核心可移植到 Micronaut runtime（ADR 0004）。
- 未来 adapters 可能包括 Spring Security、Quartz scheduling、Redis locks/events、Kubernetes workers、Docker workers 或其它 persistence implementations。

### Bootstrap Modules

- `graphpilot-bootstrap-spring` — 负责把选定 adapters 装配为 Spring Boot application。默认 profile 装配 Spring Web adapter 与 in-memory persistence adapter，使 Workflow API 可在无数据库配置时本地运行；`postgres` profile 装配 PostgreSQL/MyBatis/Flyway 持久化能力，并要求显式提供 PostgreSQL 连接环境变量。
- `graphpilot-bootstrap-micronaut` — Micronaut 端到端运行时 PoC，装配 in-memory persistence adapter、框架中立 worker 核心与 Micronaut worker 胶水，并暴露与 Spring 对齐的 Workflow/Run/Task/Timeline HTTP API。E2E 测试覆盖 create/activate/trigger/query，证明同一份 application/domain/worker core 可在非 Spring runtime 下完成 DAG 执行并持久化 task output。

## 前端架构

前端计划采用 Next.js，并使用 TypeScript、Tailwind CSS、Zustand、React Flow、TanStack Query 和 shadcn/ui。

## 当前 Workflow Run 与 Worker 能力

当前后端支持通过 Spring Web API 与 Micronaut runtime API 触发 ACTIVE Workflow 的一次运行，并由框架中立 worker core 执行 DAG。Workflow task definitions 支持静态 JSON `config`，shell/http/mock/poc handlers 以该 config 作为输入。Task config 表达式在 application worker coordinator 中、handler 执行之前完成解析，因此 handlers 接收到的是普通解析后的 config map，无需感知表达式机制。

已暴露的运行相关端点包括：

- `POST /api/workflows/{workflowId}/runs`
- `GET /api/workflows/{workflowId}/runs?limit=50`
- `GET /api/workflow-runs/{runId}`
- `GET /api/workflow-runs/{runId}/tasks`
- `GET /api/workflow-runs/{runId}/timeline?limit=200`

Worker 支持事件驱动执行、重试退避、task output 持久化、结构化 timeline，以及保守的 PENDING-run scanner。Scanner 只扫描并执行 `PENDING` runs，不自动重置或失败化长期卡住的 `RUNNING` runs/tasks。

## 待确认的架构问题

- Workflow definitions 应该通过代码、UI metadata、YAML 创建，还是三者都支持？
- 第一版 persistence adapter 应该采用 MyBatis XML、MyBatis annotations，还是 jOOQ？
- 第一版 execution adapter 应该采用 local worker、shell executor、HTTP executor，还是 container executor？
- 哪些能力属于 open core，哪些属于 commercial enterprise features？
