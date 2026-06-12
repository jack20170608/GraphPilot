# GraphPilot 图驭

GraphPilot is a modern DAG-based orchestration platform for reliable task scheduling, workflow automation, and enterprise operations.

GraphPilot 图驭是一个面向企业级任务调度、数据编排和自动化运维的现代 DAG 工作流平台。

## Product Direction

GraphPilot aims to combine the openness and developer experience of modern workflow engines with the operational reliability expected from enterprise schedulers.

Key goals:

- Model workflows as explicit DAGs.
- Provide reliable scheduling, retry, backfill, dependency, and SLA handling.
- Offer a clear web console for workflow design, monitoring, and operations.
- Support extensible task execution across scripts, APIs, containers, Kubernetes jobs, data pipelines, and enterprise systems.
- Keep the core platform open and composable while leaving room for commercial enterprise features.

## Technology Direction

GraphPilot uses a modern frontend with a Java backend designed around hexagonal architecture.

```text
Frontend:
  Next.js + TypeScript + Tailwind CSS + Zustand
  React Flow + TanStack Query + shadcn/ui

Backend:
  Java 21 + Maven multi-module
  Framework-free domain and application core
  Spring Boot as the first web/bootstrap adapter

Infrastructure:
  PostgreSQL + Redis
  Docker Compose for local development
```

## Repository Layout

```text
GraphPilot/
  README.md
  apps/
    web/                                  # Next.js frontend
  backend/
    pom.xml                              # Maven reactor root
    graphpilot-domain/                   # Pure domain model, no framework dependencies
    graphpilot-application/              # Use cases and ports
    graphpilot-adapter-web-spring/       # Spring Web REST adapter
    graphpilot-adapter-persistence-mybatis/
    graphpilot-bootstrap-spring/         # Spring Boot assembly module
  docs/
    product/
    architecture/
      adr/
  infra/
    docker-compose.yml
  scripts/
```

## Backend Architecture Rule

Backend dependencies point inward only:

```text
bootstrap -> adapters -> application -> domain
```

The `domain` and `application` modules must not depend on Spring, Servlet APIs, MyBatis, JPA, Redis, Quartz, or security frameworks.

## Current Status

Project skeleton initialized. Product scope, domain model, and concrete MVP features are still being designed.
