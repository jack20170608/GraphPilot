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
    graphpilot-adapter-persistence-mybatis/
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

项目骨架已初始化。产品范围、领域模型和具体 MVP 功能仍在设计中。
