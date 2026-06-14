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
- `graphpilot-adapter-persistence-mybatis` — MyBatis persistence adapter。启用 `postgres` profile 时，它通过 PostgreSQL 与 Flyway schema 为 Workflow create/get/list 提供持久化实现。
- 未来 adapters 可能包括 Spring Security、Quartz scheduling、Redis locks/events、Kubernetes workers、Docker workers 或其它 persistence implementations。

### Bootstrap Module

- `graphpilot-bootstrap-spring` — 负责把选定 adapters 装配为 Spring Boot application。默认 profile 装配 Spring Web adapter 与 in-memory persistence adapter，使 Workflow API 可在无数据库配置时本地运行；`postgres` profile 装配 PostgreSQL/MyBatis/Flyway 持久化能力，并要求显式提供 PostgreSQL 连接环境变量。

## 前端架构

前端计划采用 Next.js，并使用 TypeScript、Tailwind CSS、Zustand、React Flow、TanStack Query 和 shadcn/ui。

## 待确认的架构问题

- Workflow definitions 应该通过代码、UI metadata、YAML 创建，还是三者都支持？
- 第一版 persistence adapter 应该采用 MyBatis XML、MyBatis annotations，还是 jOOQ？
- 第一版 execution adapter 应该采用 local worker、shell executor、HTTP executor，还是 container executor？
- 哪些能力属于 open core，哪些属于 commercial enterprise features？
