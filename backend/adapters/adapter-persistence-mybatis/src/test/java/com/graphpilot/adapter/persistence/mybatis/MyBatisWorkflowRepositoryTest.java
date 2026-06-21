package com.graphpilot.adapter.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.shared.port.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
class MyBatisWorkflowRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorkflowRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WorkflowPersistenceConfiguration.class)
    static class TestApplication {
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table workflow_edges, workflow_tasks, workflows restart identity cascade");
    }

    @Test
    void saveThenFindByIdRestoresWorkflowFieldsAndDagOrder() {
        Workflow workflow = workflow(
                "workflow-1",
                "Daily ETL",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(task("extract", "Extract data"), task("transform", "Transform data"), task("load", "Load data")),
                List.of(edge("extract", "transform"), edge("transform", "load")));

        Workflow savedWorkflow = repository.save(workflow);

        assertThat(savedWorkflow).isEqualTo(workflow);
        assertThat(repository.findById(workflow.id()))
                .hasValueSatisfying(found -> assertWorkflowEquals(found, workflow));
    }

    @Test
    void savesAndLoadsTaskConfig() {
        Workflow workflow = workflow(
                "workflow-config",
                "Config Workflow",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(new TaskDefinition(
                        TaskId.of("shell"),
                        "Shell task",
                        "shell",
                        TaskConfig.of(Map.of("command", "echo hi", "timeout", 10)))),
                List.of());

        repository.save(workflow);

        Workflow found = repository.findById(workflow.id()).orElseThrow();
        TaskDefinition task = found.dag().tasks().getFirst();
        assertThat(task.config().getString("command")).contains("echo hi");
        assertThat(task.config().getLong("timeout")).contains(10L);
    }

    @Test
    void saveThenFindByIdRestoresWorkflowStatus() {
        Workflow workflow = workflow(
                "workflow-active",
                "Active Workflow",
                Instant.parse("2026-06-13T00:00:00Z"))
                .activate();

        repository.save(workflow);

        assertThat(repository.findById(workflow.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(WorkflowStatus.ACTIVE));
    }

    @Test
    void saveUpdatesExistingWorkflowStatus() {
        Workflow draftWorkflow = workflow(
                "workflow-status-update",
                "Status Update Workflow",
                Instant.parse("2026-06-13T00:00:00Z"));
        Workflow activeWorkflow = draftWorkflow.activate();

        repository.save(draftWorkflow);
        repository.save(activeWorkflow);

        assertThat(repository.findById(activeWorkflow.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(WorkflowStatus.ACTIVE));
    }

    @Test
    void missingIdReturnsEmpty() {
        assertThat(repository.findById(WorkflowId.of("missing-workflow"))).isEmpty();
    }

    @Test
    void findAllOrdersByCreatedAtThenIdAndAppliesLimit() {
        Workflow workflowB = workflow("workflow-b", "Workflow B", Instant.parse("2026-06-13T00:00:00Z"));
        Workflow workflowA = workflow(
                "workflow-a",
                "Workflow A",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(task("extract", "Extract data"), task("transform", "Transform data"), task("load", "Load data")),
                List.of(edge("extract", "transform")))
                .activate();
        Workflow workflowC = workflow("workflow-c", "Workflow C", Instant.parse("2026-06-13T00:01:00Z"));

        repository.save(workflowC);
        repository.save(workflowB);
        repository.save(workflowA);

        assertThat(repository.findAll(10))
                .extracting(workflow -> workflow.id().value())
                .containsExactly("workflow-a", "workflow-b", "workflow-c");

        List<Workflow> limitedWorkflows = repository.findAll(2);
        assertThat(limitedWorkflows)
                .extracting(workflow -> workflow.id().value())
                .containsExactly("workflow-a", "workflow-b");
        assertThat(limitedWorkflows.getFirst().status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(limitedWorkflows.getFirst().dag().tasks())
                .extracting(task -> task.id().value())
                .containsExactly("extract", "transform", "load");
        assertThat(limitedWorkflows.getFirst().dag().edges())
                .extracting(edge -> edge.fromTaskId().value() + "->" + edge.toTaskId().value())
                .containsExactly("extract->transform");
    }

    @Test
    void saveReplacesExistingTasksAndEdgesForSameWorkflow() {
        Workflow original = workflow(
                "workflow-replace",
                "Replaceable Workflow",
                Instant.parse("2026-06-13T00:00:00Z"),
                List.of(task("extract", "Extract data"), task("transform", "Transform data"), task("load", "Load data")),
                List.of(edge("extract", "transform"), edge("transform", "load")));
        Workflow replacement = workflow(
                "workflow-replace",
                "Replacement Workflow",
                Instant.parse("2026-06-13T00:05:00Z"),
                List.of(task("prepare", "Prepare data"), task("publish", "Publish data")),
                List.of(edge("prepare", "publish")));

        repository.save(original);
        repository.save(replacement);

        assertThat(repository.findById(replacement.id()))
                .hasValueSatisfying(found -> assertWorkflowEquals(found, replacement));
    }

    @Test
    void findAllRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> repository.findAll(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow query limit must be positive");
    }

    private static Workflow workflow(String id, String name, Instant createdAt) {
        return workflow(
                id,
                name,
                createdAt,
                List.of(task("extract", "Extract data")),
                List.of());
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

    private static void assertWorkflowEquals(Workflow actual, Workflow expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.name()).isEqualTo(expected.name());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.dag().tasks()).containsExactlyElementsOf(expected.dag().tasks());
        assertThat(actual.dag().edges()).containsExactlyElementsOf(expected.dag().edges());
    }
}
