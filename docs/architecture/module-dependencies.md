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
    DOMAIN["graphpilot-domain<br/><i>纯领域模型</i>"]
    APP["graphpilot-application<br/><i>use cases + ports</i>"]

    %% 适配器层
    PERSISTENCE_MEM["graphpilot-adapter-persistence-memory"]
    PERSISTENCE_MYBATIS["graphpilot-adapter-persistence-mybatis"]
    ADAPTER_WORKER["graphpilot-adapter-worker"]
    ADAPTER_WORKER_HTTP["graphpilot-adapter-worker-http"]
    ADAPTER_WEB["graphpilot-adapter-web-spring"]

    %% 核心模块之间的通信
    SCHEDULER -- "HTTP POST /api/worker/execute" --> WORKER
    ADMIN -- "触发 workflow run" --> SCHEDULER

    %% 每个 core 模块自包含依赖
    SCHEDULER --> APP
    SCHEDULER --> DOMAIN
    SCHEDULER --> PERSISTENCE_MEM
    SCHEDULER --> PERSISTENCE_MYBATIS
    SCHEDULER --> ADAPTER_WORKER
    SCHEDULER --> ADAPTER_WORKER_HTTP

    WORKER --> APP
    WORKER --> ADAPTER_WORKER
    WORKER --> ADAPTER_WORKER_HTTP

    ADMIN --> APP
    ADMIN --> DOMAIN
    ADMIN --> PERSISTENCE_MEM
    ADMIN --> PERSISTENCE_MYBATIS
    ADMIN --> ADAPTER_WEB

    %% 共享层依赖
    APP --> DOMAIN

    %% 分层样式
    classDef core fill:#fbcfe8,stroke:#9d174d,color:#000
    classDef domain fill:#fde68a,stroke:#b45309,color:#000
    classDef app fill:#bfdbfe,stroke:#1d4ed8,color:#000
    classDef adapter fill:#bbf7d0,stroke:#15803d,color:#000

    class SCHEDULER,WORKER,ADMIN core
    class DOMAIN domain
    class APP app
    class PERSISTENCE_MEM,PERSISTENCE_MYBATIS,ADAPTER_WORKER,ADAPTER_WORKER_HTTP,ADAPTER_WEB adapter
```

## 模块清单（11 个）

| 模块 | 说明 |
|------|------|
| **scheduler** | 调度核心（端口 8080）：DAG 调度、任务触发、重试 |
| **worker** | 任务执行（端口 8081）：shell/mock handlers |
| **admin** | 管理 API（端口 8082）：Workflow CRUD、前端接口 |
| graphpilot-domain | 共享领域模型 |
| graphpilot-application | 共享应用层（use cases + ports） |
| graphpilot-adapter-persistence-memory | 内存持久化（测试用） |
| graphpilot-adapter-persistence-mybatis | MyBatis 持久化（生产用） |
| graphpilot-adapter-worker | Worker 核心（shell/mock handler） |
| graphpilot-adapter-worker-http | Worker HTTP 传输 |
| graphpilot-adapter-web-spring | Spring Web 适配器 |

## 依赖矩阵

每个核心模块自包含其所需的 adapter：

| 模块 | 直接依赖 |
|------|----------|
| **scheduler** | `application`, `domain`, `adapter-persistence-*`, `adapter-worker`, `adapter-worker-http` |
| **worker** | `application`, `adapter-worker`, `adapter-worker-http` |
| **admin** | `application`, `domain`, `adapter-persistence-*`, `adapter-web-spring` |

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