# CLAUDE.md

本文件为 Claude Code 在 GraphPilot 仓库中工作时提供项目级指导。

## 项目规则

1. 所有项目文档都必须使用中文编写，除非用户明确要求使用其它语言。
2. 后端项目使用 JDK 21。生成 Java 代码时优先使用 JDK 21 的现代语言特性，例如 records、sealed types、pattern matching、switch expressions、text blocks、不可变集合和虚拟线程适用场景；但不得为了炫技牺牲可读性、可测试性或 Hexagonal Architecture 边界。
3. 当前开发终端是 Git Bash。即使运行在 Windows 系统上，也应优先使用 Bash 风格命令；除非确有必要或用户明确要求，避免使用 Windows PowerShell 专用命令。
4. 本项目优先使用 auto mode 风格自动化执行：在已有明确目标、验证路径和安全边界时，Claude Code 应尽量直接执行读写、测试、commit、普通 `git push` 等操作，减少反复向用户确认；但仍不得绕过明确的 deny 规则、不得执行 force push、破坏性删除或其它高风险操作。
5. **新研发项目，无需向后兼容**：架构演进时可以大胆废弃旧代码、重构模块结构、删除不再使用的文件。不保留过时的向后兼容层。

## 项目概览

GraphPilot 图驭是一个现代化的 DAG 任务编排平台，面向可靠任务调度、工作流自动化和企业级运维场景。

## 当前技术方向

### 前端

- Next.js
- TypeScript
- Tailwind CSS
- Zustand
- React Flow
- TanStack Query
- shadcn/ui

### 后端

- Java 21
- Maven multi-module
- **3 核心模块架构**：scheduler（调度）→ worker（执行）→ admin（管理 API）
- Hexagonal Architecture / Ports and Adapters
- Spring Boot
- MyBatis 作为 persistence adapter

### 基础设施

- PostgreSQL
- Redis
- Docker Compose 用于本地开发

## 仓库结构

```text
GraphPilot/
  apps/
    web/                 # Next.js 前端
  backend/
    pom.xml             # Maven reactor 根配置
    scheduler/          # 调度核心（端口 8080）：DAG 调度、任务触发、重试
    worker/             # 任务执行（端口 8081）：shell/mock handlers
    admin/              # 管理 API（端口 8082）：Workflow CRUD、前端接口
    graphpilot-domain/  # 共享领域模型
    graphpilot-application/  # Use cases 和 ports
    graphpilot-adapter-*/    # 适配器层
  docs/
    product/
    architecture/
      adr/
  infra/
  scripts/
```

## 3 核心模块架构

后端采用 **3 核心业务模块** 架构，依赖方向：

```text
admin → scheduler → worker
```

### scheduler（调度器）

- 端口：8080
- 职责：DAG 依赖解析、任务触发、失败重试、PENDING 扫描补偿
- 依赖：`application`、`domain`、`adapter-persistence-*`、`adapter-worker-*`

### worker（执行器）

- 端口：8081
- 职责：接收 scheduler 分发的任务，使用注册 handlers（shell/mock）执行任务
- 依赖：`adapter-worker`、`adapter-worker-http`

### admin（管理 API）

- 端口：8082
- 职责：Workflow CRUD、WorkflowRun 创建与查询、前端 REST API
- 依赖：`application`、`domain`、`adapter-persistence-*`、`adapter-web-spring`

### 进程间通信

```
admin (8082) ──触发 workflow run──→ scheduler (8080) ──HTTP POST──→ worker (8081)
```

## 后端架构规则

后端采用 Hexagonal Architecture。依赖方向只能向内：

```text
adapters -> application -> domain
```

### Domain 模块规则

`backend/graphpilot-domain` 必须保持 framework-free。

允许：

- Java standard library
- Domain models
- Value objects
- Domain services
- Domain events
- Domain exceptions

禁止：

- Spring annotations or APIs
- Servlet/Jakarta web APIs
- MyBatis/JPA/Hibernate annotations
- Redis/PostgreSQL drivers
- Quartz APIs
- Security framework APIs
- Jackson annotations

### Application 模块规则

`backend/graphpilot-application` 只能依赖 `graphpilot-domain`。

允许：

- Inbound ports
- Outbound ports
- Commands and queries
- Use case implementations
- Application services

禁止：

- Spring annotations or APIs
- Persistence implementation details
- HTTP/web DTOs
- Security framework types
- Redis/Quartz/MyBatis/JPA APIs

### Adapter 模块规则

Adapter 可以使用具体框架 API，但业务规则必须保留在 domain/application 中。

示例：

- Web controllers 负责把 HTTP DTOs 映射为 application commands/use cases。
- Persistence adapters 负责把数据库记录映射为 domain objects。
- Worker adapters 负责执行任务，而不是承载 DAG 业务逻辑。

### 3 核心模块规则

每个核心模块（scheduler/worker/admin）是独立的 Spring Boot 应用：

- 各自有独立的 `*Application.java` 入口
- 各自有 `*Configuration.java` 进行依赖装配
- 不包含核心业务逻辑，只负责装配 adapters 和启动运行时

## 构建与验证

后端验证命令：

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
```

修改后端 POM 或 Java 代码后，应运行这些命令。

## 编码偏好

- 优先使用小而内聚的模块和文件。
- 在可行时保持 domain objects 不可变。
- 在系统边界处验证输入。
- 显式处理错误，不要静默吞掉失败。
- 对非平凡的 domain/application 行为，先补测试再实现。
- 保持 adapter DTOs/entities 与 domain models 分离。

## 文档

持久化的架构决策记录在：

```text
docs/architecture/adr/
```

当项目结构或主要技术选择变化时，同步更新 `README.md` 和 `docs/architecture/overview.md`。