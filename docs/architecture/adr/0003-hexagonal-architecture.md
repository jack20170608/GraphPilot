# ADR 0003: Hexagonal Backend Architecture

## Status

Accepted

## Context

GraphPilot's backend domain model should not be tied to a specific web container, dependency injection framework, persistence technology, security layer, scheduler, or worker implementation.

The platform should be able to evolve from Spring Web + MyBatis to other adapters without rewriting the domain and application core.

## Decision

Use hexagonal architecture, also known as ports and adapters.

The backend dependency direction is:

```text
bootstrap -> adapters -> application -> domain
```

Rules:

- `graphpilot-domain` has no framework dependencies.
- `graphpilot-application` depends only on `graphpilot-domain`.
- Adapter modules depend on `graphpilot-application` and implement its outbound ports or invoke its inbound ports.
- `graphpilot-bootstrap-spring` composes selected adapters and starts the Spring Boot runtime.
- Business rules live in domain/application, not adapters.

## Consequences

- The core model is easier to test without a Spring context.
- Persistence, web, scheduler, lock, event, and worker technologies can be swapped by adding adapters.
- Maven module boundaries help enforce architectural dependency direction.
- Adapters require explicit mapping between external DTOs/entities and core domain objects.
