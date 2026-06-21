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
adapters -> application -> domain
```

详细的模块间依赖关系见 [模块依赖图](./module-dependencies.md)。

### Core Modules（3 核心模块）

- `scheduler` — 调度核心（端口 8080）：DAG 依赖解析、任务触发、失败重试、PENDING 扫描补偿
- `worker` — 任务执行（端口 8081）：使用注册 handlers（shell/mock）执行任务
- `admin` — 管理 API（端口 8082）：Workflow CRUD、前端 REST API

### Shared Modules

- `domain` — 纯领域模型（无框架依赖）：domain models, value objects, domain events, domain exceptions
- `application-shared` — 共享 ports：WorkflowRepository, ClockPort, IdGeneratorPort, WorkflowNotFoundException
- `scheduler-application` — 调度专属 use cases：TriggerWorkflowRunUseCase, ExecuteWorkflowRunUseCase, ScanPendingWorkflowRunsUseCase
- `admin-application` — 管理专属 use cases：CreateWorkflowUseCase, QueryWorkflowUseCase, ChangeWorkflowLifecycleUseCase

### Adapter Modules（adapters 聚合）

- `adapters/` — 适配器聚合模块
  - `adapter-persistence-memory` — 内存持久化（本地开发/测试用）
  - `adapter-persistence-mybatis` — MyBatis 持久化（生产用）
  - `adapter-worker` — 框架中立 worker 核心：task handlers（shell/mock），仅依赖 scheduler-application
  - `adapter-worker-http` — Worker HTTP 传输适配器
  - `adapter-web-spring` — Spring Web REST 适配器

> 适配器命名遵循 `adapter-*` 模式，artifactId 与目录名一致。

### 设计原则

1. **应用层拆分**：按使用方拆分，scheduler 用 scheduler-application，admin 用 admin-application
2. **共享 ports**：跨模块共享的 port 放在 application-shared，避免循环依赖
3. **模块自包含**：3 核心模块各自依赖自己的 application 模块和 adapters

### 进程间通信

```
admin (8082) ──触发 workflow run──→ scheduler (8080) ──HTTP POST──→ worker (8081)
```

## 前端架构

前端采用 Next.js，并使用 TypeScript、Tailwind CSS、Zustand、React Flow、TanStack Query 和 shadcn/ui。

## 相关文档

- [模块依赖图](./module-dependencies.md)
- [ADR: Hexagonal Architecture](./adr/0003-hexagonal-architecture.md)