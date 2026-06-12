# ADR 0003: 后端 Hexagonal Architecture

## 状态

已接受

## 背景

GraphPilot 的后端 domain model 不应绑定到特定 Web container、Dependency Injection framework、Persistence technology、Security layer、Scheduler 或 Worker implementation。

平台应该能够从 Spring Web + MyBatis 演进到其它 adapters，而不需要重写 domain 和 application core。

## 决策

采用 Hexagonal Architecture，也称为 Ports and Adapters。

后端依赖方向为：

```text
bootstrap -> adapters -> application -> domain
```

规则：

- `graphpilot-domain` 不包含 framework dependencies。
- `graphpilot-application` 只依赖 `graphpilot-domain`。
- Adapter modules 依赖 `graphpilot-application`，并实现其 outbound ports 或调用其 inbound ports。
- `graphpilot-bootstrap-spring` 负责组合选定 adapters 并启动 Spring Boot runtime。
- Business rules 位于 domain/application，而不是 adapters。

## 影响

- 核心模型更容易在不启动 Spring context 的情况下测试。
- Persistence、web、scheduler、lock、event 和 worker 技术可以通过新增 adapters 替换。
- Maven module boundaries 有助于强制约束架构依赖方向。
- Adapters 需要在 external DTOs/entities 与 core domain objects 之间进行显式映射。
