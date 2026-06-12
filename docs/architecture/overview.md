# GraphPilot Architecture Overview

GraphPilot is designed as a DAG-first task orchestration platform with a Java backend following hexagonal architecture and a modern Next.js web console.

## System Areas

- DAG definition and validation
- Scheduler and dependency resolver
- Task execution runtime
- Metadata store
- API service
- Web console
- Observability and alerting
- Plugin and integration system

## Backend Architecture

The backend uses ports and adapters so the core model is not bound to a specific web container, dependency injection framework, persistence layer, security layer, or scheduler implementation.

Dependency direction:

```text
bootstrap -> adapters -> application -> domain
```

### Core Modules

- `graphpilot-domain` — pure domain model, value objects, domain services, domain events, and domain exceptions.
- `graphpilot-application` — use cases, inbound ports, outbound ports, commands, queries, and application services.

### Adapter Modules

- `graphpilot-adapter-web-spring` — Spring Web REST adapter.
- `graphpilot-adapter-persistence-mybatis` — MyBatis persistence adapter.
- Future adapters may include Spring Security, Quartz scheduling, Redis locks/events, Kubernetes workers, Docker workers, or alternative persistence implementations.

### Bootstrap Module

- `graphpilot-bootstrap-spring` — Spring Boot application that wires selected adapters together.

## Frontend Architecture

The frontend is planned as a Next.js application using TypeScript, Tailwind CSS, Zustand, React Flow, TanStack Query, and shadcn/ui.

## Open Architectural Questions

- Should workflow definitions be created as code, UI metadata, YAML, or all three?
- Which persistence adapter should be implemented first: MyBatis XML, MyBatis annotations, or jOOQ?
- Which execution adapter should be first: local worker, shell executor, HTTP executor, or container executor?
- What belongs in open core versus commercial enterprise features?
