package com.graphpilot.adapter.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowRunRepositoryTest {

    private static final Instant TRIGGERED_AT = Instant.parse("2026-06-14T00:00:00Z");

    private final InMemoryWorkflowRunRepository repository = new InMemoryWorkflowRunRepository();

    @Test
    void savesAndFindsRunById() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        List<TaskRun> taskRuns = List.of(taskRun("task-run-1", workflowRun.id(), "task-1", 0));

        WorkflowRun savedWorkflowRun = repository.save(workflowRun, taskRuns);

        assertThat(savedWorkflowRun).isEqualTo(workflowRun);
        assertThat(repository.findRunById(workflowRun.id())).contains(workflowRun);
    }

    @Test
    void findsRunsByWorkflowIdSortedByTriggeredAtThenIdWithLimit() {
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun secondAtSameTime = workflowRun(
                "run-b",
                workflowId,
                Instant.parse("2026-06-14T00:00:00Z"));
        WorkflowRun firstAtSameTime = workflowRun(
                "run-a",
                workflowId,
                Instant.parse("2026-06-14T00:00:00Z"));
        WorkflowRun later = workflowRun(
                "run-c",
                workflowId,
                Instant.parse("2026-06-14T00:01:00Z"));
        WorkflowRun otherWorkflowRun = workflowRun(
                "run-other",
                "workflow-2",
                Instant.parse("2026-06-13T00:00:00Z"));

        repository.save(later, List.of());
        repository.save(secondAtSameTime, List.of());
        repository.save(otherWorkflowRun, List.of());
        repository.save(firstAtSameTime, List.of());

        assertThat(repository.findRunsByWorkflowId(workflowId, 10))
                .containsExactly(firstAtSameTime, secondAtSameTime, later);
        assertThat(repository.findRunsByWorkflowId(workflowId, 2))
                .containsExactly(firstAtSameTime, secondAtSameTime);
    }

    @Test
    void findsTaskRunsByRunIdSortedByPositionThenTaskId() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        TaskRun secondPositionSameTask = taskRun("task-run-c", workflowRun.id(), "task-c", 1);
        TaskRun secondPositionFirstTask = taskRun("task-run-a", workflowRun.id(), "task-a", 1);
        TaskRun firstPosition = taskRun("task-run-b", workflowRun.id(), "task-b", 0);

        repository.save(workflowRun, List.of(secondPositionSameTask, secondPositionFirstTask, firstPosition));

        assertThat(repository.findTaskRunsByRunId(workflowRun.id()))
                .containsExactly(firstPosition, secondPositionFirstTask, secondPositionSameTask);
    }

    @Test
    void rejectsNonPositiveWorkflowRunQueryLimit() {
        assertThatThrownBy(() -> repository.findRunsByWorkflowId(WorkflowId.of("workflow-1"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow run query limit must be positive");
    }

    @Test
    void returnsImmutableDefensiveTaskRunLists() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        TaskRun originalTaskRun = taskRun("task-run-1", workflowRun.id(), "task-1", 0);
        TaskRun addedAfterSave = taskRun("task-run-2", workflowRun.id(), "task-2", 1);
        List<TaskRun> taskRuns = new ArrayList<>(List.of(originalTaskRun));

        repository.save(workflowRun, taskRuns);
        taskRuns.add(addedAfterSave);

        List<TaskRun> foundTaskRuns = repository.findTaskRunsByRunId(workflowRun.id());

        assertThat(foundTaskRuns).containsExactly(originalTaskRun);
        assertThatThrownBy(() -> foundTaskRuns.add(addedAfterSave))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generatesUniqueWorkflowRunIds() {
        UuidWorkflowRunIdGenerator idGenerator = new UuidWorkflowRunIdGenerator();

        WorkflowRunId firstWorkflowRunId = idGenerator.nextWorkflowRunId();
        WorkflowRunId secondWorkflowRunId = idGenerator.nextWorkflowRunId();

        assertThat(firstWorkflowRunId.value()).isNotBlank();
        assertThat(secondWorkflowRunId.value()).isNotBlank();
        assertThat(firstWorkflowRunId).isNotEqualTo(secondWorkflowRunId);
    }

    @Test
    void updateStatusTransitionsWorkflowRunToRunningAndPersistsStartedAt() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        repository.save(workflowRun, List.of());
        Instant startedAt = Instant.parse("2026-06-14T00:00:05Z");

        repository.updateStatus(workflowRun.id(), WorkflowRunStatus.RUNNING, startedAt);

        WorkflowRun reloaded = repository.findRunById(workflowRun.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkflowRunStatus.RUNNING);
        assertThat(reloaded.startedAt()).isEqualTo(startedAt);
        assertThat(reloaded.finishedAt()).isNull();
    }

    @Test
    void updateStatusSetsFinishedAtOnTerminalStatus() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        repository.save(workflowRun, List.of());
        Instant startedAt = Instant.parse("2026-06-14T00:00:05Z");

        repository.updateStatus(workflowRun.id(), WorkflowRunStatus.SUCCEEDED, startedAt);

        WorkflowRun reloaded = repository.findRunById(workflowRun.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkflowRunStatus.SUCCEEDED);
        assertThat(reloaded.finishedAt()).isNotNull();
    }

    @Test
    void updateTaskRunStatusReplacesMatchingTaskRunById() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        TaskRun taskRun = taskRun("task-run-1", workflowRun.id(), "task-1", 0);
        repository.save(workflowRun, List.of(taskRun));

        TaskRun running = taskRun.withStatus(TaskRunStatus.RUNNING);
        repository.updateTaskRunStatus(workflowRun.id(), running);

        List<TaskRun> reloaded = repository.findTaskRunsByRunId(workflowRun.id());
        assertThat(reloaded).containsExactly(running);
        assertThat(reloaded.get(0).status()).isEqualTo(TaskRunStatus.RUNNING);
    }

    @Test
    void updateTaskRunStatusPreservesUnmatchedTaskRuns() {
        WorkflowRun workflowRun = workflowRun("run-1", "workflow-1", TRIGGERED_AT);
        TaskRun first = taskRun("task-run-1", workflowRun.id(), "task-1", 0);
        TaskRun second = taskRun("task-run-2", workflowRun.id(), "task-2", 1);
        repository.save(workflowRun, List.of(first, second));

        repository.updateTaskRunStatus(workflowRun.id(), second.withStatus(TaskRunStatus.SUCCEEDED));

        List<TaskRun> reloaded = repository.findTaskRunsByRunId(workflowRun.id());
        assertThat(reloaded).extracting(TaskRun::id, TaskRun::status)
                .containsExactly(
                        tuple(TaskRunId.of("task-run-1"), TaskRunStatus.PENDING),
                        tuple(TaskRunId.of("task-run-2"), TaskRunStatus.SUCCEEDED));
    }

    @Test
    void findByStatusFiltersByStatusWithLimit() {
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun pending = workflowRun("run-a", workflowId, TRIGGERED_AT);
        WorkflowRun running = workflowRun("run-b", workflowId, TRIGGERED_AT);
        WorkflowRun anotherPending = workflowRun("run-c", workflowId, TRIGGERED_AT);
        repository.save(pending, List.of());
        repository.save(running, List.of());
        repository.save(anotherPending, List.of());

        repository.updateStatus(running.id(), WorkflowRunStatus.RUNNING, TRIGGERED_AT);

        assertThat(repository.findByStatus(WorkflowRunStatus.PENDING, 10))
                .extracting(WorkflowRun::id)
                .containsExactly(WorkflowRunId.of("run-a"), WorkflowRunId.of("run-c"));
        assertThat(repository.findByStatus(WorkflowRunStatus.PENDING, 1))
                .extracting(WorkflowRun::id)
                .containsExactly(WorkflowRunId.of("run-a"));
        assertThat(repository.findByStatus(WorkflowRunStatus.RUNNING, 10))
                .extracting(WorkflowRun::id)
                .containsExactly(WorkflowRunId.of("run-b"));
    }

    @Test
    void generatesUniqueTaskRunIds() {
        UuidTaskRunIdGenerator idGenerator = new UuidTaskRunIdGenerator();

        TaskRunId firstTaskRunId = idGenerator.nextTaskRunId();
        TaskRunId secondTaskRunId = idGenerator.nextTaskRunId();

        assertThat(firstTaskRunId.value()).isNotBlank();
        assertThat(secondTaskRunId.value()).isNotBlank();
        assertThat(firstTaskRunId).isNotEqualTo(secondTaskRunId);
    }

    private static WorkflowRun workflowRun(String id, String workflowId, Instant triggeredAt) {
        return workflowRun(id, WorkflowId.of(workflowId), triggeredAt);
    }

    private static WorkflowRun workflowRun(String id, WorkflowId workflowId, Instant triggeredAt) {
        return WorkflowRun.restore(
                WorkflowRunId.of(id),
                workflowId,
                WorkflowRunStatus.PENDING,
                triggeredAt);
    }

    private static TaskRun taskRun(String id, WorkflowRunId workflowRunId, String taskId, int position) {
        return TaskRun.restore(
                TaskRunId.of(id),
                workflowRunId,
                TaskId.of(taskId),
                "Task " + taskId,
                TaskRunStatus.PENDING,
                position,
                TRIGGERED_AT);
    }
}
