# MyBatis Workflow Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a PostgreSQL/MyBatis implementation of `WorkflowRepository` with Flyway schema migration and Testcontainers verification, while keeping the default runtime on the existing memory repository.

**Architecture:** Keep `domain` and `application` unchanged. Implement persistence in `graphpilot-adapter-persistence-mybatis`, then wire it into `graphpilot-bootstrap-spring` only under the `postgres` Spring profile. Use Flyway for schema creation and Testcontainers PostgreSQL for adapter/bootstrap integration tests.

**Tech Stack:** Java 21, Maven multi-module, Spring Boot 3.3.5, MyBatis Spring Boot Starter 3.0.3, Flyway, PostgreSQL 16, Testcontainers, JUnit 5, AssertJ.

---

## Scope and Safety Notes

- Do not change REST API contracts in this plan.
- Do not add workflow lifecycle, workflow run, scheduler, pagination metadata, or API response envelopes.
- Keep SQL parameterized through MyBatis placeholders; never concatenate request/user input into SQL.
- Default Spring profile must still start without PostgreSQL.
- Commit steps below are part of the execution plan. If the current session policy or user instruction does not authorize commits, stop at the verification step and ask before committing.

## File Structure

### Create

- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java`
  - Implements `WorkflowRepository`; owns transaction boundary and domain mapping.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/WorkflowPersistenceConfiguration.java`
  - Spring config active under `postgres` profile; scans MyBatis mapper package and exposes repository bean.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.java`
  - MyBatis mapper interface for workflow/task/edge rows.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java`
  - Persistence row record for `workflows`.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowTaskRow.java`
  - Persistence row record for `workflow_tasks`.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowEdgeRow.java`
  - Persistence row record for `workflow_edges`.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`
  - SQL statements for mapper interface.
- `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql`
  - Flyway migration for workflow tables and indexes.
- `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java`
  - Testcontainers repository integration tests.
- `backend/graphpilot-bootstrap-spring/src/main/resources/application-postgres.yml`
  - Runtime profile config for datasource, Flyway, and MyBatis.
- `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java`
  - Testcontainers bootstrap/profile integration test.

### Modify

- `backend/pom.xml`
  - Add Testcontainers version property if Spring Boot dependency management is insufficient for module-local test dependencies.
- `backend/graphpilot-adapter-persistence-mybatis/pom.xml`
  - Add Flyway and test dependencies.
- `backend/graphpilot-bootstrap-spring/pom.xml`
  - Add MyBatis persistence adapter and Testcontainers test dependencies.
- `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java`
  - Make memory repository bean inactive under `postgres` profile.
- `README.md`
  - Document default memory profile and optional PostgreSQL profile.
- `docs/architecture/overview.md`
  - Update MyBatis adapter status from placeholder to profile-enabled persistence adapter.

---

## Task 1: Add Flyway/Testcontainers dependencies for MyBatis adapter

**Files:**
- Modify: `backend/graphpilot-adapter-persistence-mybatis/pom.xml`

- [ ] **Step 1: Update the MyBatis adapter POM**

Replace `backend/graphpilot-adapter-persistence-mybatis/pom.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.graphpilot</groupId>
        <artifactId>graphpilot-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>graphpilot-adapter-persistence-mybatis</artifactId>
    <name>GraphPilot MyBatis Persistence Adapter</name>
    <description>MyBatis persistence adapter for GraphPilot outbound ports.</description>

    <dependencies>
        <dependency>
            <groupId>com.graphpilot</groupId>
            <artifactId>graphpilot-application</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis-spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Verify dependency resolution**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit dependency changes if authorized**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/pom.xml
git commit -m "chore: add mybatis persistence test dependencies"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 2: Add Flyway migration for workflow tables

**Files:**
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql`

- [ ] **Step 1: Create the migration file**

Create `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql`:

```sql
create table workflows (
    id varchar(128) primary key,
    name varchar(255) not null,
    created_at timestamptz not null
);

create table workflow_tasks (
    workflow_id varchar(128) not null references workflows(id) on delete cascade,
    task_id varchar(128) not null,
    name varchar(255) not null,
    position integer not null,
    primary key (workflow_id, task_id)
);

create table workflow_edges (
    workflow_id varchar(128) not null references workflows(id) on delete cascade,
    source_task_id varchar(128) not null,
    target_task_id varchar(128) not null,
    position integer not null,
    primary key (workflow_id, source_task_id, target_task_id)
);

create index idx_workflows_created_at_id on workflows(created_at, id);
create index idx_workflow_tasks_workflow_position on workflow_tasks(workflow_id, position);
create index idx_workflow_edges_workflow_position on workflow_edges(workflow_id, position);
```

- [ ] **Step 2: Run targeted resource-aware build**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit migration if authorized**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/src/main/resources/db/migration/V1__create_workflow_tables.sql
git commit -m "feat: add workflow persistence schema"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 3: Add persistence row records

**Files:**
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowRow.java`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowTaskRow.java`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row/WorkflowEdgeRow.java`

- [ ] **Step 1: Create `WorkflowRow`**

```java
package com.graphpilot.adapter.persistence.mybatis.row;

import java.time.Instant;
import java.util.Objects;

public record WorkflowRow(String id, String name, Instant createdAt) {

    public WorkflowRow {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
```

- [ ] **Step 2: Create `WorkflowTaskRow`**

```java
package com.graphpilot.adapter.persistence.mybatis.row;

import java.util.Objects;

public record WorkflowTaskRow(
        String workflowId,
        String taskId,
        String name,
        int position) {

    public WorkflowTaskRow {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
```

- [ ] **Step 3: Create `WorkflowEdgeRow`**

```java
package com.graphpilot.adapter.persistence.mybatis.row;

import java.util.Objects;

public record WorkflowEdgeRow(
        String workflowId,
        String sourceTaskId,
        String targetTaskId,
        int position) {

    public WorkflowEdgeRow {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(sourceTaskId, "sourceTaskId must not be null");
        Objects.requireNonNull(targetTaskId, "targetTaskId must not be null");
    }
}
```

- [ ] **Step 4: Compile the adapter**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit row records if authorized**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/row
git commit -m "feat: add workflow persistence row records"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 4: Add MyBatis mapper interface and XML

**Files:**
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.java`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper/WorkflowMapper.xml`

- [ ] **Step 1: Create mapper interface**

Create `WorkflowMapper.java`:

```java
package com.graphpilot.adapter.persistence.mybatis.mapper;

import com.graphpilot.adapter.persistence.mybatis.row.WorkflowEdgeRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowTaskRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowMapper {

    void upsertWorkflow(WorkflowRow workflow);

    void deleteTasksByWorkflowId(@Param("workflowId") String workflowId);

    void deleteEdgesByWorkflowId(@Param("workflowId") String workflowId);

    void insertTask(WorkflowTaskRow task);

    void insertEdge(WorkflowEdgeRow edge);

    WorkflowRow findWorkflowById(@Param("workflowId") String workflowId);

    List<WorkflowTaskRow> findTasksByWorkflowId(@Param("workflowId") String workflowId);

    List<WorkflowEdgeRow> findEdgesByWorkflowId(@Param("workflowId") String workflowId);

    List<WorkflowRow> findAllWorkflows(@Param("limit") int limit);
}
```

- [ ] **Step 2: Create mapper XML**

Create `WorkflowMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper">

    <resultMap id="workflowRowMap" type="com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow">
        <constructor>
            <arg column="id" javaType="java.lang.String" />
            <arg column="name" javaType="java.lang.String" />
            <arg column="created_at" javaType="java.time.Instant" />
        </constructor>
    </resultMap>

    <resultMap id="workflowTaskRowMap" type="com.graphpilot.adapter.persistence.mybatis.row.WorkflowTaskRow">
        <constructor>
            <arg column="workflow_id" javaType="java.lang.String" />
            <arg column="task_id" javaType="java.lang.String" />
            <arg column="name" javaType="java.lang.String" />
            <arg column="position" javaType="int" />
        </constructor>
    </resultMap>

    <resultMap id="workflowEdgeRowMap" type="com.graphpilot.adapter.persistence.mybatis.row.WorkflowEdgeRow">
        <constructor>
            <arg column="workflow_id" javaType="java.lang.String" />
            <arg column="source_task_id" javaType="java.lang.String" />
            <arg column="target_task_id" javaType="java.lang.String" />
            <arg column="position" javaType="int" />
        </constructor>
    </resultMap>

    <insert id="upsertWorkflow">
        insert into workflows (id, name, created_at)
        values (#{id}, #{name}, #{createdAt})
        on conflict (id) do update set
            name = excluded.name,
            created_at = excluded.created_at
    </insert>

    <delete id="deleteTasksByWorkflowId">
        delete from workflow_tasks
        where workflow_id = #{workflowId}
    </delete>

    <delete id="deleteEdgesByWorkflowId">
        delete from workflow_edges
        where workflow_id = #{workflowId}
    </delete>

    <insert id="insertTask">
        insert into workflow_tasks (workflow_id, task_id, name, position)
        values (#{workflowId}, #{taskId}, #{name}, #{position})
    </insert>

    <insert id="insertEdge">
        insert into workflow_edges (workflow_id, source_task_id, target_task_id, position)
        values (#{workflowId}, #{sourceTaskId}, #{targetTaskId}, #{position})
    </insert>

    <select id="findWorkflowById" resultMap="workflowRowMap">
        select id, name, created_at
        from workflows
        where id = #{workflowId}
    </select>

    <select id="findTasksByWorkflowId" resultMap="workflowTaskRowMap">
        select workflow_id, task_id, name, position
        from workflow_tasks
        where workflow_id = #{workflowId}
        order by position asc, task_id asc
    </select>

    <select id="findEdgesByWorkflowId" resultMap="workflowEdgeRowMap">
        select workflow_id, source_task_id, target_task_id, position
        from workflow_edges
        where workflow_id = #{workflowId}
        order by position asc, source_task_id asc, target_task_id asc
    </select>

    <select id="findAllWorkflows" resultMap="workflowRowMap">
        select id, name, created_at
        from workflows
        order by created_at asc, id asc
        limit #{limit}
    </select>
</mapper>
```

- [ ] **Step 3: Compile the adapter**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit mapper if authorized**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/mapper backend/graphpilot-adapter-persistence-mybatis/src/main/resources/com/graphpilot/adapter/persistence/mybatis/mapper
git commit -m "feat: add workflow mybatis mapper"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 5: Write failing repository integration tests

**Files:**
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java`

- [ ] **Step 1: Create the failing test class**

Create `MyBatisWorkflowRepositoryTest.java`:

```java
package com.graphpilot.adapter.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MybatisTest
@Testcontainers
@ActiveProfiles("postgres")
@Import(WorkflowPersistenceConfiguration.class)
class MyBatisWorkflowRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private WorkflowRepository repository;

    @Test
    void saveAndFindByIdRestoresWorkflow() {
        Workflow workflow = workflow(
                "workflow-a",
                "Daily ETL",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(task("extract", "Extract data"), task("load", "Load data")),
                List.of(edge("extract", "load")));

        repository.save(workflow);

        assertThat(repository.findById(WorkflowId.of("workflow-a")))
                .hasValueSatisfying(found -> {
                    assertThat(found.id().value()).isEqualTo("workflow-a");
                    assertThat(found.name().value()).isEqualTo("Daily ETL");
                    assertThat(found.createdAt()).isEqualTo(Instant.parse("2026-06-13T00:00:00Z"));
                    assertThat(found.dag().tasks())
                            .extracting(task -> task.id().value())
                            .containsExactly("extract", "load");
                    assertThat(found.dag().edges())
                            .extracting(edge -> edge.fromTaskId().value() + "->" + edge.toTaskId().value())
                            .containsExactly("extract->load");
                });
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(repository.findById(WorkflowId.of("missing-workflow"))).isEmpty();
    }

    @Test
    void findAllOrdersByCreatedAtThenIdAndAppliesLimit() {
        repository.save(workflow("workflow-b", "Workflow B", Instant.parse("2026-06-13T00:00:02Z")));
        repository.save(workflow("workflow-c", "Workflow C", Instant.parse("2026-06-13T00:00:01Z")));
        repository.save(workflow("workflow-a", "Workflow A", Instant.parse("2026-06-13T00:00:01Z")));

        List<Workflow> workflows = repository.findAll(2);

        assertThat(workflows)
                .extracting(workflow -> workflow.id().value())
                .containsExactly("workflow-a", "workflow-c");
    }

    @Test
    void saveReplacesExistingTasksAndEdgesForWorkflow() {
        repository.save(workflow(
                "workflow-replace",
                "Original",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(task("extract", "Extract"), task("load", "Load")),
                List.of(edge("extract", "load"))));

        repository.save(workflow(
                "workflow-replace",
                "Replacement",
                Instant.parse("2026-06-13T00:01:00Z"),
                List.of(task("single", "Single task")),
                List.of()));

        assertThat(repository.findById(WorkflowId.of("workflow-replace")))
                .hasValueSatisfying(found -> {
                    assertThat(found.name().value()).isEqualTo("Replacement");
                    assertThat(found.createdAt()).isEqualTo(Instant.parse("2026-06-13T00:01:00Z"));
                    assertThat(found.dag().tasks())
                            .extracting(task -> task.id().value())
                            .containsExactly("single");
                    assertThat(found.dag().edges()).isEmpty();
                });
    }

    @Test
    void rejectsNonPositiveFindAllLimit() {
        assertThatThrownBy(() -> repository.findAll(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow query limit must be positive");
    }

    private static Workflow workflow(String id, String name, Instant createdAt) {
        return workflow(id, name, createdAt, List.of(task("task", "Task")), List.of());
    }

    private static Workflow workflow(
            String id,
            String name,
            Instant createdAt,
            List<TaskDefinition> tasks,
            List<DagEdge> edges) {
        return Workflow.create(
                WorkflowId.of(id),
                WorkflowName.of(name),
                new DagDefinition(tasks, edges),
                createdAt);
    }

    private static TaskDefinition task(String id, String name) {
        return new TaskDefinition(TaskId.of(id), name);
    }

    private static DagEdge edge(String fromTaskId, String toTaskId) {
        return new DagEdge(TaskId.of(fromTaskId), TaskId.of(toTaskId));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails for missing repository/config implementation**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRepositoryTest
```

Expected before implementation: FAIL because `WorkflowPersistenceConfiguration` or `MyBatisWorkflowRepository` does not exist.

- [ ] **Step 3: Do not commit failing tests unless the team accepts red commits**

Leave the failing test uncommitted and continue to Task 6.

---

## Task 6: Implement MyBatis repository and Spring configuration

**Files:**
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepository.java`
- Create: `backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis/WorkflowPersistenceConfiguration.java`

- [ ] **Step 1: Create `WorkflowPersistenceConfiguration`**

```java
package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
@MapperScan(basePackageClasses = WorkflowMapper.class)
public class WorkflowPersistenceConfiguration {

    @Bean
    WorkflowRepository workflowRepository(WorkflowMapper workflowMapper) {
        return new MyBatisWorkflowRepository(workflowMapper);
    }
}
```

- [ ] **Step 2: Create `MyBatisWorkflowRepository`**

```java
package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowEdgeRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowTaskRow;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public final class MyBatisWorkflowRepository implements WorkflowRepository {

    private final WorkflowMapper workflowMapper;

    public MyBatisWorkflowRepository(WorkflowMapper workflowMapper) {
        this.workflowMapper = Objects.requireNonNull(workflowMapper, "workflowMapper must not be null");
    }

    @Override
    @Transactional
    public Workflow save(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        String workflowId = workflow.id().value();

        workflowMapper.upsertWorkflow(toWorkflowRow(workflow));
        workflowMapper.deleteEdgesByWorkflowId(workflowId);
        workflowMapper.deleteTasksByWorkflowId(workflowId);
        toTaskRows(workflow).forEach(workflowMapper::insertTask);
        toEdgeRows(workflow).forEach(workflowMapper::insertEdge);

        return workflow;
    }

    @Override
    public Optional<Workflow> findById(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        return Optional.ofNullable(workflowMapper.findWorkflowById(workflowId.value()))
                .map(this::toWorkflow);
    }

    @Override
    public List<Workflow> findAll(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Workflow query limit must be positive");
        }
        return workflowMapper.findAllWorkflows(limit).stream()
                .map(this::toWorkflow)
                .toList();
    }

    private Workflow toWorkflow(WorkflowRow workflowRow) {
        List<TaskDefinition> tasks = workflowMapper.findTasksByWorkflowId(workflowRow.id()).stream()
                .map(this::toTaskDefinition)
                .toList();
        List<DagEdge> edges = workflowMapper.findEdgesByWorkflowId(workflowRow.id()).stream()
                .map(this::toDagEdge)
                .toList();
        return Workflow.create(
                WorkflowId.of(workflowRow.id()),
                WorkflowName.of(workflowRow.name()),
                new DagDefinition(tasks, edges),
                workflowRow.createdAt());
    }

    private static WorkflowRow toWorkflowRow(Workflow workflow) {
        return new WorkflowRow(
                workflow.id().value(),
                workflow.name().value(),
                workflow.createdAt());
    }

    private static List<WorkflowTaskRow> toTaskRows(Workflow workflow) {
        List<TaskDefinition> tasks = workflow.dag().tasks();
        List<WorkflowTaskRow> rows = new ArrayList<>(tasks.size());
        for (int index = 0; index < tasks.size(); index++) {
            TaskDefinition task = tasks.get(index);
            rows.add(new WorkflowTaskRow(
                    workflow.id().value(),
                    task.id().value(),
                    task.name(),
                    index));
        }
        return List.copyOf(rows);
    }

    private static List<WorkflowEdgeRow> toEdgeRows(Workflow workflow) {
        List<DagEdge> edges = workflow.dag().edges();
        List<WorkflowEdgeRow> rows = new ArrayList<>(edges.size());
        for (int index = 0; index < edges.size(); index++) {
            DagEdge edge = edges.get(index);
            rows.add(new WorkflowEdgeRow(
                    workflow.id().value(),
                    edge.fromTaskId().value(),
                    edge.toTaskId().value(),
                    index));
        }
        return List.copyOf(rows);
    }

    private TaskDefinition toTaskDefinition(WorkflowTaskRow row) {
        return new TaskDefinition(TaskId.of(row.taskId()), row.name());
    }

    private DagEdge toDagEdge(WorkflowEdgeRow row) {
        return new DagEdge(TaskId.of(row.sourceTaskId()), TaskId.of(row.targetTaskId()));
    }
}
```

- [ ] **Step 3: Run repository test**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-adapter-persistence-mybatis -am test -Dtest=MyBatisWorkflowRepositoryTest
```

Expected: `BUILD SUCCESS`, all 5 tests pass.

- [ ] **Step 4: Commit repository implementation if authorized**

```bash
git add backend/graphpilot-adapter-persistence-mybatis/src/main/java/com/graphpilot/adapter/persistence/mybatis backend/graphpilot-adapter-persistence-mybatis/src/test/java/com/graphpilot/adapter/persistence/mybatis/MyBatisWorkflowRepositoryTest.java
git commit -m "feat: add mybatis workflow repository"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 7: Wire bootstrap profile and dependencies

**Files:**
- Modify: `backend/graphpilot-bootstrap-spring/pom.xml`
- Modify: `backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java`
- Create: `backend/graphpilot-bootstrap-spring/src/main/resources/application-postgres.yml`

- [ ] **Step 1: Add MyBatis adapter dependency to bootstrap POM**

In `backend/graphpilot-bootstrap-spring/pom.xml`, add this dependency after the memory adapter dependency:

```xml
        <dependency>
            <groupId>com.graphpilot</groupId>
            <artifactId>graphpilot-adapter-persistence-mybatis</artifactId>
            <version>${project.version}</version>
        </dependency>
```

Also add Testcontainers dependencies before `</dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Make default memory repository inactive under postgres profile**

Modify `WorkflowAssemblyConfiguration.java` to import `Profile`:

```java
import org.springframework.context.annotation.Profile;
```

Change the repository bean to:

```java
    @Bean
    @Profile("!postgres")
    WorkflowRepository workflowRepository() {
        return new InMemoryWorkflowRepository();
    }
```

- [ ] **Step 3: Add postgres profile config**

Create `backend/graphpilot-bootstrap-spring/src/main/resources/application-postgres.yml`:

```yaml
spring:
  datasource:
    url: ${GRAPHPILOT_POSTGRES_URL}
    username: ${GRAPHPILOT_POSTGRES_USER}
    password: ${GRAPHPILOT_POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    clean-disabled: true

mybatis:
  mapper-locations: classpath*:com/graphpilot/adapter/persistence/mybatis/mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 4: Verify default profile still uses memory**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=GraphPilotApplicationTest
```

Expected: `BUILD SUCCESS`; existing `loadsWorkflowBeans` still asserts `InMemoryWorkflowRepository`.

- [ ] **Step 5: Commit bootstrap wiring if authorized**

```bash
git add backend/graphpilot-bootstrap-spring/pom.xml backend/graphpilot-bootstrap-spring/src/main/java/com/graphpilot/bootstrap/spring/WorkflowAssemblyConfiguration.java backend/graphpilot-bootstrap-spring/src/main/resources/application-postgres.yml
git commit -m "feat: wire postgres persistence profile"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 8: Add bootstrap postgres profile integration test

**Files:**
- Create: `backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java`

- [ ] **Step 1: Create postgres profile API integration test**

```java
package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRepository;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("postgres")
class PostgresWorkflowApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    void usesMyBatisWorkflowRepositoryWhenPostgresProfileIsActive() {
        assertThat(workflowRepository).isInstanceOf(MyBatisWorkflowRepository.class);
    }

    @Test
    void createsGetsAndListsWorkflowThroughHttpApiUsingPostgresRepository() {
        ResponseEntity<Map<String, Object>> createResponse = postWorkflow("""
                {
                  "name": "Postgres Workflow",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "load" }
                  ]
                }
                """);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsKey("id");
        String workflowId = createResponse.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> getResponse = getWorkflow(workflowId);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("id", workflowId);
        assertThat(getResponse.getBody()).containsEntry("name", "Postgres Workflow");
        assertThat(getResponse.getBody().get("tasks")).asList().hasSize(2);
        assertThat(getResponse.getBody().get("edges")).asList().hasSize(1);

        ResponseEntity<List<Map<String, Object>>> listResponse = listWorkflows();
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .extracting(workflow -> workflow.get("id"))
                .contains(workflowId);
    }

    private ResponseEntity<Map<String, Object>> postWorkflow(String requestBody) {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.POST,
                jsonEntity(requestBody),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> getWorkflow(String workflowId) {
        return restTemplate.exchange(
                "/api/workflows/" + workflowId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<List<Map<String, Object>>> listWorkflows() {
        return restTemplate.exchange(
                "/api/workflows",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});
    }

    private static HttpEntity<String> jsonEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }
}
```

- [ ] **Step 2: Run postgres profile integration test**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=PostgresWorkflowApiIntegrationTest
```

Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 3: Run existing default bootstrap tests**

Run:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=GraphPilotApplicationTest,QueryWorkflowApiIntegrationTest
```

Expected: `BUILD SUCCESS`; default profile tests still use memory repository.

- [ ] **Step 4: Commit postgres integration test if authorized**

```bash
git add backend/graphpilot-bootstrap-spring/src/test/java/com/graphpilot/bootstrap/spring/PostgresWorkflowApiIntegrationTest.java
git commit -m "test: cover postgres workflow api profile"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 9: Update project documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture/overview.md`

- [ ] **Step 1: Update README current status**

In `README.md`, add or update a backend status section with this content in Chinese:

```markdown
## 当前后端状态

后端已经完成 Workflow 定义的早期纵向切片：

- `POST /api/workflows` 创建 Workflow。
- `GET /api/workflows/{id}` 查询单个 Workflow。
- `GET /api/workflows?limit=50` 查询 Workflow 列表。

默认 profile 使用 in-memory persistence adapter，适合无数据库的本地开发和快速测试。
启用 `postgres` profile 时，后端使用 PostgreSQL、Flyway 和 MyBatis 持久化 Workflow 定义。

本地 PostgreSQL 可通过 `infra/docker-compose.yml` 启动；自动化持久化测试使用 Testcontainers，不依赖手动启动的数据库。
```

- [ ] **Step 2: Update architecture overview adapter status**

In `docs/architecture/overview.md`, replace the MyBatis adapter bullet with:

```markdown
- `graphpilot-adapter-persistence-mybatis` — PostgreSQL/MyBatis persistence adapter。启用 `postgres` profile 时通过 Flyway migration 初始化 schema，并为 Workflow create/get/list API 提供持久化存储。
```

Replace the bootstrap module paragraph with:

```markdown
- `graphpilot-bootstrap-spring` — 负责把选定 adapters 装配为 Spring Boot application。默认 profile 装配 Spring Web adapter 与 in-memory persistence adapter，使 Workflow API 可在无数据库配置时本地运行；`postgres` profile 装配 PostgreSQL/MyBatis/Flyway persistence adapter。
```

- [ ] **Step 3: Commit documentation if authorized**

```bash
git add README.md docs/architecture/overview.md
git commit -m "docs: document postgres persistence profile"
```

If commits are not authorized in the current session, do not run the commit command.

---

## Task 10: Run full backend verification

**Files:**
- Verify all backend modules.

- [ ] **Step 1: Run Maven validate**

```bash
mvn -f backend/pom.xml validate
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run Maven compile**

```bash
mvn -f backend/pom.xml compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run full backend tests**

```bash
mvn -f backend/pom.xml test
```

Expected: `BUILD SUCCESS`, including MyBatis/Testcontainers tests. If Docker is unavailable, report the Testcontainers failure exactly and run the non-container tests separately:

```bash
mvn -f backend/pom.xml -pl graphpilot-bootstrap-spring -am test -Dtest=!PostgresWorkflowApiIntegrationTest
```

- [ ] **Step 4: Inspect git status and diff**

```bash
git status --short --branch
git diff --stat
```

Expected: only planned files changed.

- [ ] **Step 5: Run mandatory code review agents**

Use the project-required reviewers after code changes:

1. `ecc:java-reviewer` for Java code quality and Spring/MyBatis patterns.
2. `ecc:security-reviewer` because SQL/database configuration and external input persistence are involved.
3. `ecc:database-reviewer` for schema/query review.

Address CRITICAL and HIGH issues before completion.

- [ ] **Step 6: Final commit if authorized and not already committed task-by-task**

```bash
git add backend README.md docs/architecture/overview.md
git commit -m "feat: add postgres workflow persistence profile"
```

If task-by-task commits were already created, skip this final aggregate commit.

---

## Self-Review

- Spec coverage:
  - PostgreSQL/MyBatis `WorkflowRepository`: Tasks 3-6.
  - Flyway schema migration: Task 2.
  - Default memory profile preserved: Task 7 and Task 8 default regression command.
  - `postgres` profile wiring: Tasks 7-8.
  - Testcontainers verification: Tasks 5 and 8.
  - Documentation updates: Task 9.
  - Verification and mandatory reviews: Task 10.
- Placeholder scan: no `TBD`, `TODO`, `implement later`, or unspecified test steps are intentionally left.
- Type consistency:
  - `WorkflowRepository` method signatures match `backend/graphpilot-application/src/main/java/com/graphpilot/application/workflow/port/out/WorkflowRepository.java`.
  - Test helper constructors use existing domain types: `Workflow`, `WorkflowId`, `WorkflowName`, `DagDefinition`, `TaskDefinition`, `TaskId`, `DagEdge`.
  - Mapper XML namespace matches `com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper`.
