# GraphPilot 模块依赖图

本文档描述 GraphPilot 后端 Maven 多模块之间的依赖关系。后端采用 **3 核心业务模块** 架构：

```text
admin (管理) → scheduler (调度) → worker (执行)
```

> 图中箭头方向 `A --> B` 表示 **A 依赖 B**。

## 模块依赖图

```mermaid
graph TD
    %% 3 核心业务模块
    SCHEDULER["scheduler<br/><i>DAG 调度、任务触发、重试</i>"]
    WORKER["worker<br/><i>任务执行 handler</i>"]
    ADMIN["admin<br/><i>Workflow CRUD、API</i>"]

    %% 共享层
    DOMAIN["domain<br/><i>纯领域模型</i>"]
    APP_SHARED["application-shared<br/><i>共享 ports</i>"]
    SCHEDULER_APP["scheduler-application<br/><i>调度 use cases</i>"]
    ADMIN_APP["admin-application<br/><i>管理 use cases</i>"]

    %% 适配器层（聚合模块）
    ADAPTERS["adapters<br/><i>适配器聚合模块</i>"]
    
    PERSISTENCE_MEM["adapter-persistence-memory"]
    PERSISTENCE_MYBATIS["adapter-persistence-mybatis"]
    ADAPTER_WORKER["adapter-worker"]
    ADAPTER_WORKER_HTTP["adapter-worker-http"]
    ADAPTER_WEB["adapter-web-spring"]

    %% 核心模块之间的通信
    SCHEDULER -- "HTTP POST /api/worker/execute" --> WORKER
    ADMIN -- "触发 workflow run" --> SCHEDULER

    %% 每个 core 模块自包含依赖
    SCHEDULER --> SCHEDULER_APP
    SCHEDULER --> APP_SHARED
    SCHEDULER --> DOMAIN
    SCHEDULER --> ADAPTERS

    WORKER --> SCHEDULER_APP
    WORKER --> ADAPTERS

    ADMIN --> ADMIN_APP
    ADMIN --> APP_SHARED
    ADMIN --> DOMAIN
    ADMIN --> ADAPTERS

    %% 共享层依赖
    APP_SHARED --> DOMAIN
    SCHEDULER_APP --> APP_SHARED
    SCHEDULER_APP --> DOMAIN
    ADMIN_APP --> APP_SHARED
    ADMIN_APP --> DOMAIN

    %% 分层样式
    classDef core fill:#fbcfe8,stroke:#9d174d,color:#000
    classDef domain fill:#fde68a,stroke:#b45309,color:#000
    classDef app fill:#bfdbfe,stroke:#1d4ed8,color:#000
    classDef adapters fill:#9ca3af,stroke:#4b5563,color:#000
    classDef adapter fill:#bbf7d0,stroke:#15803d,color:#000

    class SCHEDULER,WORKER,ADMIN core
    class DOMAIN domain
    class APP_SHARED,SCHEDULER_APP,ADMIN_APP app
    class ADAPTERS adapters
    class PERSISTENCE_MEM,PERSISTENCE_MYBATIS,ADAPTER_WORKER,ADAPTER_WORKER_HTTP,ADAPTER_WEB adapter
```

## 模块清单（14 个）

| 模块 | 说明 |
|------|------|
| **核心模块** | |
| scheduler | 调度核心（端口 8080）：DAG 调度、任务触发、重试 |
| worker | 任务执行（端口 8081）：shell/mock handlers |
| admin | 管理 API（端口 8082）：Workflow CRUD、前端接口 |
| **共享层** | |
| domain | 共享领域模型（无框架依赖） |
| application-shared | 共享 ports（WorkflowRepository, ClockPort, IdGeneratorPort） |
| scheduler-application | 调度专属 use cases（TriggerWorkflowRun, ExecuteWorkflowRun 等） |
| admin-application | 管理专属 use cases（CreateWorkflow, QueryWorkflow 等） |
| **适配器层**（adapters 聚合） | |
| adapter-persistence-memory | 内存持久化（测试用） |
| adapter-persistence-mybatis | MyBatis 持久化（生产用） |
| adapter-worker | Worker 核心（shell/mock handler） |
| adapter-worker-http | Worker HTTP 传输 |
| adapter-web-spring | Spring Web 适配器 |

## 依赖矩阵

每个核心模块自包含其所需的 adapter 和 application：

| 模块 | 直接依赖 |
|------|----------|
| **scheduler** | `scheduler-application`, `application-shared`, `domain`, `adapters` |
| **worker** | `scheduler-application`, `adapters` |
| **admin** | `admin-application`, `application-shared`, `domain`, `adapters` |

## 设计原则

### 1. 应用层拆分
- **scheduler-application**：仅scheduler使用，包含执行相关的 use cases
- **admin-application**：仅admin使用，包含工作流管理相关的 use cases
- **application-shared**：跨模块共享的 ports，避免循环依赖

### 2. 适配器聚合
所有 adapter 放在 `adapters/` 聚合模块下，命名统一为 `adapter-*` 格式

### 3. 模块自包含
- 每个核心模块（scheduler/worker/admin）自包含其依赖
- 不再依赖 monolithic 的 application 模块

## 进程间通信

```
┌─────────────┐     POST /api/worker/execute     ┌─────────────┐
│  scheduler  │ ──────────────────────────────────→│   worker    │
│   (8080)    │                                   │   (8081)    │
└─────────────┘                                   └─────────────┘
       ↑
       │ 创建 WorkflowRun
       │
┌─────────────┐
│   admin     │
│   (8082)    │
└─────────────┘
```

- **admin → scheduler**：创建 WorkflowRun（触发调度）
- **scheduler → worker**：HTTP POST 分发任务执行（remote 模式）

## 配置

### scheduler (application.yml)

```yaml
server:
  port: 8080
graphpilot:
  scheduler:
    scanner:
      enabled: true
      interval-ms: 10000
  worker:
    dispatch:
      mode: local  # local 或 remote
```

### worker (application.yml)

```yaml
server:
  port: 8081
graphpilot:
  worker:
    handlers: [shell, mock]
```

### admin (application.yml)

```yaml
server:
  port: 8082
graphpilot:
  persistence:
    type: memory  # memory 或 mybatis
```

## 相关文档

- [架构概览](./overview.md)
- [独立 Worker 进程设计](../superpowers/specs/2026-06-21-standalone-worker-design.md)