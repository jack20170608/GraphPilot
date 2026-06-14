package com.graphpilot.adapter.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.time.Instant;
import java.util.List;
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
class MyBatisWorkflowRunRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WorkflowPersistenceConfiguration.class)
    static class TestApplication {
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table task_runs, workflow_runs, workflow_edges, workflow_tasks, workflows restart identity cascade");
    }

    @Test
    void saveThenFindRunByIdRestoresWorkflowRunFields() {
        Workflow workflow = saveActiveWorkflow("workflow-run-save");
        WorkflowRun workflowRun = workflowRun(
                "run-1",
                workflow.id(),
                WorkflowRunStatus.RUNNING,
                Instant.parse("2026-06-14T00:00:00Z"));
        List<TaskRun> taskRuns = List.of(
                taskRun("task-run-1", workflowRun.id(), "extract", "Extract", TaskRunStatus.SUCCEEDED, 0),
                taskRun("task-run-2", workflowRun.id(), "load", "Load", TaskRunStatus.PENDING, 1));

        WorkflowRun savedRun = workflowRunRepository.save(workflowRun, taskRuns);

        assertThat(savedRun).isEqualTo(workflowRun);
        assertThat(workflowRunRepository.findRunById(workflowRun.id()))
                .hasValueSatisfying(found -> assertWorkflowRunEquals(found, workflowRun));
    }

    @Test
    void findRunsByWorkflowIdOrdersByTriggeredAtThenIdAndAppliesLimit() {
        Workflow workflow = saveActiveWorkflow("workflow-run-list");
        WorkflowRun runB = workflowRun("run-b", workflow.id(), WorkflowRunStatus.PENDING, Instant.parse("2026-06-14T00:00:00Z"));
        WorkflowRun runA = workflowRun("run-a", workflow.id(), WorkflowRunStatus.SUCCEEDED, Instant.parse("2026-06-14T00:00:00Z"));
        WorkflowRun runC = workflowRun("run-c", workflow.id(), WorkflowRunStatus.FAILED, Instant.parse("2026-06-14T00:01:00Z"));

        workflowRunRepository.save(runC, List.of());
        workflowRunRepository.save(runB, List.of());
        workflowRunRepository.save(runA, List.of());

        assertThat(workflowRunRepository.findRunsByWorkflowId(workflow.id(), 2))
                .extracting(run -> run.id().value())
                .containsExactly("run-a", "run-b");
    }

    @Test
    void findTaskRunsByRunIdOrdersByPositionThenTaskId() {
        Workflow workflow = saveActiveWorkflow("workflow-task-run-list");
        WorkflowRun workflowRun = workflowRun(
                "run-task-list",
                workflow.id(),
                WorkflowRunStatus.PENDING,
                Instant.parse("2026-06-14T00:00:00Z"));
        TaskRun taskRunB = taskRun("task-run-b", workflowRun.id(), "task-b", "Task B", TaskRunStatus.PENDING, 1);
        TaskRun taskRunA = taskRun("task-run-a", workflowRun.id(), "task-a", "Task A", TaskRunStatus.RUNNING, 0);
        TaskRun taskRunC = taskRun("task-run-c", workflowRun.id(), "task-c", "Task C", TaskRunStatus.SKIPPED, 2);

        workflowRunRepository.save(workflowRun, List.of(taskRunB, taskRunC, taskRunA));

        assertThat(workflowRunRepository.findTaskRunsByRunId(workflowRun.id()))
                .extracting(taskRun -> taskRun.taskId().value())
                .containsExactly("task-a", "task-b", "task-c");
        assertThat(workflowRunRepository.findTaskRunsByRunId(workflowRun.id()))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(taskRunA, taskRunB, taskRunC);
    }

    @Test
    void missingRunReturnsEmpty() {
        assertThat(workflowRunRepository.findRunById(WorkflowRunId.of("missing-run"))).isEmpty();
    }

    @Test
    void findRunsByWorkflowIdRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> workflowRunRepository.findRunsByWorkflowId(WorkflowId.of("workflow-id"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow run query limit must be positive");
    }

    private Workflow saveActiveWorkflow(String workflowId) {
        Workflow workflow = Workflow.create(
                WorkflowId.of(workflowId),
                WorkflowName.of("Workflow " + workflowId),
                new DagDefinition(
                        List.of(new TaskDefinition(TaskId.of("extract"), "Extract")),
                        List.of()),
                Instant.parse("2026-06-13T00:00:00Z"))
                .activate();
        return workflowRepository.save(workflow);
    }

    private static WorkflowRun workflowRun(
            String id,
            WorkflowId workflowId,
            WorkflowRunStatus status,
            Instant triggeredAt) {
        return WorkflowRun.restore(WorkflowRunId.of(id), workflowId, status, triggeredAt);
    }

    private static TaskRun taskRun(
            String id,
            WorkflowRunId workflowRunId,
            String taskId,
            String taskName,
            TaskRunStatus status,
            int position) {
        return TaskRun.restore(
                TaskRunId.of(id),
                workflowRunId,
                TaskId.of(taskId),
                taskName,
                status,
                position,
                Instant.parse("2026-06-14T00:00:00Z"));
    }

    private static void assertWorkflowRunEquals(WorkflowRun actual, WorkflowRun expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.workflowId()).isEqualTo(expected.workflowId());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.triggeredAt()).isEqualTo(expected.triggeredAt());
    }
}
