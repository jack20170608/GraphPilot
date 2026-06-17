# GraphPilot 图驭

GraphPilot 图驭是一个现代化的 DAG 任务编排平台，面向可靠任务调度、工作流自动化和企业级运维场景。

## 产品方向

GraphPilot 目标是在现代工作流引擎的开放性、开发体验，与企业级调度平台所要求的运行可靠性之间取得平衡。

核心目标：

- 使用明确的 DAG 对工作流建模。
- 提供可靠的调度、重试、补数、依赖管理和 SLA 处理能力。
- 提供清晰的 Web Console，用于工作流设计、监控和运维操作。
- 支持可扩展的任务执行模型，覆盖脚本、API、容器、Kubernetes Job、数据流水线和企业系统。
- 保持核心平台开放且可组合，同时为未来商业版企业能力保留空间。

## 技术方向

GraphPilot 使用现代前端技术栈，并采用基于 Hexagonal Architecture 的 Java 后端。

```text
前端:
  Next.js + TypeScript + Tailwind CSS + Zustand
  React Flow + TanStack Query + shadcn/ui

后端:
  Java 21 + Maven multi-module
  framework-free domain and application core
  Spring Boot as the first web/bootstrap adapter

基础设施:
  PostgreSQL + Redis
  Docker Compose for local development
```

## 仓库结构

```text
GraphPilot/
  README.md
  apps/
    web/                                  # Next.js 前端
  backend/
    pom.xml                              # Maven reactor 根配置
    graphpilot-domain/                   # 纯领域模型，不依赖框架
    graphpilot-application/              # Use cases 和 ports
    graphpilot-adapter-web-spring/       # Spring Web REST adapter
    graphpilot-adapter-persistence-memory/ # 本地开发与早期纵向切片内存适配器
    graphpilot-adapter-persistence-mybatis/
    graphpilot-adapter-worker/           # 框架中立的 worker 核心（handlers + registry）
    graphpilot-adapter-worker-spring/    # Spring 事件胶水（worker 核心的 Spring 托管适配）
    graphpilot-bootstrap-spring/         # Spring Boot 组装模块
  docs/
    product/
    architecture/
      adr/
  infra/
    docker-compose.yml
  scripts/
```

## 后端架构规则

后端依赖方向只能向内：

```text
bootstrap -> adapters -> application -> domain
```

`domain` 和 `application` 模块不能依赖 Spring、Servlet APIs、MyBatis、JPA、Redis、Quartz 或安全框架。

## 当前状态

后端已完成 Workflow 基础纵向切片和 Workflow Run MVP。当前 Spring Web adapter 暴露以下 API：

- `POST /api/workflows` 创建 Workflow。
- `GET /api/workflows/{id}` 查询单个 Workflow。
- `GET /api/workflows?limit=50` 按限制数量列出 Workflows。
- `POST /api/workflows/{workflowId}/runs` 手动触发 ACTIVE Workflow 的一次运行，创建 `PENDING` run/task-run 元数据。
- `GET /api/workflows/{workflowId}/runs?limit=50` 查询某个 Workflow 的运行列表。
- `GET /api/workflow-runs/{runId}` 查询单个 Workflow Run。
- `GET /api/workflow-runs/{runId}/tasks` 查询 Workflow Run 下的 Task Run 列表。

Workflow Run MVP 只覆盖手动触发和运行元数据查询；尚未实现 scheduler、worker 执行、重试、取消、超时、日志、输出或运行状态更新能力。

默认 profile 使用 in-memory persistence，适合无数据库配置的本地开发和快速验证。启用 `postgres` profile 时，后端使用 PostgreSQL、Flyway 和 MyBatis 提供 Workflow 与 Workflow Run 元数据持久化能力，并要求显式提供以下环境变量，不提供默认值：

- `GRAPHPILOT_POSTGRES_URL`
- `GRAPHPILOT_POSTGRES_USER`
- `GRAPHPILOT_POSTGRES_PASSWORD`

本地 PostgreSQL 可通过 `infra/docker-compose.yml` 启动。自动化持久化测试使用 Testcontainers；当 Docker 不可用时，相关测试会跳过而不是失败。
