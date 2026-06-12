# ADR 0002: Technology Stack

## Status

Accepted

## Context

GraphPilot needs to support a modern DAG editing and operations console while also providing a reliable enterprise-oriented backend for task orchestration, scheduling, and execution.

## Decision

Use the following initial technology stack:

```text
Frontend:
  Next.js
  TypeScript
  Tailwind CSS
  Zustand
  React Flow
  TanStack Query
  shadcn/ui

Backend:
  Java 21
  Maven multi-module
  Hexagonal architecture
  Spring Boot as the first bootstrap and web adapter

Infrastructure:
  PostgreSQL
  Redis
  Docker Compose for local development
```

## Consequences

- Java 21 and Maven fit enterprise backend development and long-running services.
- The backend can expose Spring Boot adapters first while keeping core logic framework-independent.
- Next.js and Tailwind support a modern, product-quality web console.
- React Flow is well suited to DAG visualization and editing.
- PostgreSQL and Redis provide a practical foundation for metadata, coordination, queues, and locks.
