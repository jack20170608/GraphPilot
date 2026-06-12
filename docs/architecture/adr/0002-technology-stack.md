# ADR 0002: 技术栈

## 状态

已接受

## 背景

GraphPilot 需要支持现代化的 DAG 编辑与运维控制台，同时提供可靠的企业级后端，用于任务编排、调度和执行。

## 决策

采用以下初始技术栈：

```text
前端:
  Next.js
  TypeScript
  Tailwind CSS
  Zustand
  React Flow
  TanStack Query
  shadcn/ui

后端:
  Java 21
  Maven multi-module
  Hexagonal Architecture
  Spring Boot as the first bootstrap and web adapter

基础设施:
  PostgreSQL
  Redis
  Docker Compose for local development
```

## 影响

- Java 21 和 Maven 适合企业级后端开发以及长期运行的服务。
- 后端可以优先暴露 Spring Boot adapters，同时保持核心逻辑 framework-independent。
- Next.js 和 Tailwind 有助于构建现代化、产品级的 Web Console。
- React Flow 非常适合 DAG visualization 和 editing。
- PostgreSQL 和 Redis 为 metadata、coordination、queues 和 locks 提供实用基础。
