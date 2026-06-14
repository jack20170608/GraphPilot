# MyBatis Workflow 持久化设计

日期：2026-06-13

## 背景

GraphPilot 当前已经具备 Workflow 定义的早期纵向切片：

- `POST /api/workflows` 创建 Workflow。
- `GET /api/workflows/{id}` 查询单个 Workflow。
- `GET /api/workflows?limit=50` 查询 Workflow 列表。

这些 API 当前通过 `InMemoryWorkflowRepository` 保存数据。该实现适合本地开发和早期验证，但应用重启后数据会丢失，也无法支撑后续 Workflow lifecycle、run/execution、scheduler 和 metadata store 能力。

本设计将新增 PostgreSQL/MyBatis 版 `WorkflowRepository`，并使用 Flyway 管理 schema、Testcontainers 验证真实 PostgreSQL 行为。

## 目标

1. 保持现有 REST API 合约不变。
2. 保持 `domain` 和 `application` 模块 framework-free。
3. 在 `graphpilot-adapter-persistence-mybatis` 中实现 `WorkflowRepository`。
4. 使用 Flyway migration 创建 Workflow 持久化表。
5. 默认运行仍使用 memory adapter；启用 `postgres` profile 时使用 MyBatis/PostgreSQL adapter。
6. 使用 Testcontainers PostgreSQL 覆盖 repository 和 bootstrap/profile 集成测试。

## 非目标

本 slice 不实现以下能力：

- Workflow status/lifecycle。
- Workflow versioning。
- Workflow update/delete/archive API。
- Workflow run/task run/execution/scheduler。
- 分页游标、total count、过滤查询或 API response envelope。
- 生产级数据库账号拆分、审计表或权限模型。

## 推荐方案

采用 profile 切换方式：

- 默认 profile：继续装配 `InMemoryWorkflowRepository`。
- `postgres` profile：装配 MyBatis/Flyway/PostgreSQL 相关 bean。

选择该方案的原因：

- 不破坏当前无数据库的本地开发体验。
- 让现有 create/get/list API 可以切换到真实持久化存储。
- 与 Hexagonal Architecture 边界一致。
- 为后续 lifecycle、execution、scheduler 提供可靠 metadata store 基础。

## 架构边界

### 保持不变的 application port

`WorkflowRepository` 继续作为 application outbound port：

```java
Workflow save(Workflow workflow);
Optional<Workflow> findById(WorkflowId workflowId);
List<Workflow> findAll(int limit);
```

不在 application 层引入 MyBatis、Spring、Flyway 或 JDBC 类型。

### 新增 MyBatis adapter 内部组件

建议在 `graphpilot-adapter-persistence-mybatis` 中新增：

- `MyBatisWorkflowRepository`
  - 实现 `WorkflowRepository`。
  - 负责事务边界和 domain/persistence 映射。
- `WorkflowMapper`
  - MyBatis mapper 接口。
  - 只处理 SQL 操作，不暴露 domain model。
- persistence rows/records
  - `WorkflowRow`
  - `WorkflowTaskRow`
  - `WorkflowEdgeRow`
- Spring config
  - 限定在 `postgres` profile 下启用 mapper scan 和 repository bean。

## 数据模型

第一版使用三张表：

```text
workflows
- id varchar primary key
- name varchar not null
- created_at timestamptz not null

workflow_tasks
- workflow_id varchar not null references workflows(id) on delete cascade
- task_id varchar not null
- name varchar not null
- position int not null
- primary key (workflow_id, task_id)

workflow_edges
- workflow_id varchar not null references workflows(id) on delete cascade
- source_task_id varchar not null
- target_task_id varchar not null
- position int not null
- primary key (workflow_id, source_task_id, target_task_id)
```

`position` 用于稳定还原 task/edge 顺序。`findAll(limit)` 的排序语义应与 memory adapter 保持一致：

```sql
order by created_at asc, id asc
limit #{limit}
```

## Flyway migration

使用 Flyway 管理 schema。第一版 migration 命名为：

```text
V1__create_workflow_tables.sql
```

migration 放在应用 classpath 可扫描的 `db/migration` 路径下。`postgres` profile 中启用 Flyway，并保持：

- `validate-on-migrate: true`
- `clean-disabled: true`

不启用 `baseline-on-migrate`，因为这是新库初始化场景。

## Save 语义

第一版 `save` 使用 replace/upsert 风格：

1. upsert `workflows` 主表。
2. 删除该 workflow 既有 tasks 和 edges。
3. 插入当前 tasks 和 edges。

该语义需要事务保护，保证主表、task 表和 edge 表原子更新。

选择 replace/upsert 的原因：

- 当前 create API 正常不会重复生成相同 id。
- 未来 update workflow definition 可复用相同 repository 语义。
- 删除再插入子表可以避免旧 task/edge 残留导致 domain 还原错误。

## 查询与映射

### `findById`

- 先查询 `workflows`。
- 不存在时返回 `Optional.empty()`。
- 存在时查询 tasks 和 edges。
- 通过 `WorkflowId`、`WorkflowName`、`TaskDefinition`、`DagEdge`、`DagDefinition` 还原 domain model。
- 如果数据库中存在不合法 DAG 数据，应让 domain 校验失败并暴露为显式异常，而不是静默修复。

### `findAll`

- 校验 `limit > 0` 的责任仍保留在 application service 和 repository adapter 内。
- 查询 workflow rows 时按 `created_at asc, id asc` 排序并限制数量。
- 对返回的 workflow id 批量或逐个加载 tasks/edges。
- 第一版可以优先实现简单清晰的查询方式；若后续列表性能成为问题，再引入批量加载优化。

## Spring Boot 装配

建议拆分配置：

- memory repository bean：`@Profile("!postgres")`
- MyBatis repository bean：`@Profile("postgres")`
- MyBatis mapper scan：限定 `postgres` profile 或 adapter mapper 包。

`application-postgres.yml` 负责：

- datasource URL/user/password 环境变量占位。
- Flyway 配置。
- MyBatis mapper XML 路径和基础配置。

默认 profile 不要求本地 PostgreSQL 可用。

## 测试策略

### Repository integration test

使用 Testcontainers PostgreSQL 和 Flyway，覆盖：

1. `save` 后 `findById` 可还原完整 Workflow。
2. missing id 返回 `Optional.empty()`。
3. `findAll(limit)` 按 `createdAt, id` 排序。
4. `findAll(limit)` 正确限制返回数量。
5. replace save 不残留旧 tasks/edges。
6. 非正数 limit 被拒绝。

### Bootstrap/profile integration test

使用 `@ActiveProfiles("postgres")` 和 Testcontainers PostgreSQL，覆盖：

1. Spring context 在 `postgres` profile 下能启动。
2. `WorkflowRepository` bean 使用 MyBatis 实现。
3. 通过 HTTP `POST /api/workflows` 创建后，`GET /api/workflows/{id}` 可查询。
4. `GET /api/workflows` 可列出通过 HTTP 创建的数据。

### 回归验证

实现后至少运行：

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
mvn -f backend/pom.xml test
```

如需先做 targeted 验证，可运行：

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test
```

## 文档更新

实现完成后同步更新：

- `README.md`
- `docs/architecture/overview.md`

文档应说明：

- 当前默认 profile 使用 memory adapter。
- `postgres` profile 使用 MyBatis/Flyway/PostgreSQL。
- Testcontainers 用于持久化集成测试。
- `infra/docker-compose.yml` 中的 PostgreSQL 可用于本地手动运行，但自动化测试不依赖它。

## 风险与缓解

### 风险：profile 装配冲突

缓解：memory 和 MyBatis repository 使用互斥 profile；bootstrap 测试分别覆盖默认 profile 和 `postgres` profile。

### 风险：mapper XML 与接口漂移

缓解：Repository integration test 覆盖所有 mapper 方法；避免未测试 SQL。

### 风险：schema migration 未进入最终 classpath

缓解：将 migration 放在 bootstrap 可见的 classpath 路径，或确保 MyBatis adapter resources 被 bootstrap 依赖带入；用 `postgres` profile 的 SpringBootTest 验证 Flyway 自动执行。

### 风险：数据库数据无法还原为合法 domain

缓解：不绕过 domain constructors/value objects；还原时让 domain 校验继续生效，尽早暴露异常。

## 研究记录

- GitHub CLI 当前不可用，`gh search code` 渠道已跳过，原因：`gh: command not found`。
- 当前会话未暴露 Context7 MCP，无法实时查询最新官方文档。
- 设计依据来自项目现有模块结构，以及 MyBatis Spring Boot Starter、Spring Boot Flyway/Testcontainers、Testcontainers PostgreSQL/JUnit 5 的官方惯例。
