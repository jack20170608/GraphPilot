package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionCoordinatorServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    @Mock
    private WorkflowRepository workflowRepository;

    private FakeWorkflowRunRepository workflowRunRepository;
    private ConfigurableTaskHandler taskHandler;
    private TaskHandlerProvider taskHandlerProvider;
    private RecordingBackoffStrategy backoffStrategy;
    private WorkflowExecutionCoordinatorService coordinator;

    @BeforeEach
    void setUp() {
        workflowRunRepository = new FakeWorkflowRunRepository();
        taskHandler = new ConfigurableTaskHandler();
        backoffStrategy = new RecordingBackoffStrategy();
        taskHandlerProvider = new TaskHandlerProvider() {
            @Override
            public TaskHandler getHandler(String taskType) {
                return taskHandler;
            }

            @Override
            public List<TaskHandler> getAllHandlers() {
                return List.of(taskHandler);
            }
        };
        coordinator = new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                fixedClock(),
                backoffStrategy);
    }

    @Test
    void executeThrowsForNullWorkflowRunId() {
        assertThatThrownBy(() -> coordinator.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeThrowsWhenWorkflowRunNotFound() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        // No run stored in the fake repository -> findRunById returns empty.

        assertThatThrownBy(() -> coordinator.execute(runId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow run not found");
    }

    @Test
    void executeSkipsWhenAlreadySucceeded() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.SUCCEEDED));

        coordinator.execute(runId);

        assertThat(workflowRunRepository.statusUpdates).isEmpty();
        assertThat(taskHandler.invocations).isEmpty();
    }

    @Test
    void executeSkipsWhenAlreadyFailed() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.FAILED));

        coordinator.execute(runId);

        assertThat(workflowRunRepository.statusUpdates).isEmpty();
        assertThat(taskHandler.invocations).isEmpty();
    }

    @Test
    void executeMarksPendingWorkflowAsRunningAndFinalizesSucceeded() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.SUCCEEDED, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));

        coordinator.execute(runId);

        assertThat(workflowRunRepository.statusUpdates).containsExactly(
                WorkflowRunStatus.RUNNING, WorkflowRunStatus.SUCCEEDED);
    }

    @Test
    void executeRunsFullDagInTopologicalOrder() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "extract", TaskRunStatus.PENDING, 0));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "transform", TaskRunStatus.PENDING, 1));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "load", TaskRunStatus.PENDING, 2));
        givenWorkflow(workflowId, linearDag("extract", "transform", "load"));
        taskHandler.alwaysSucceed();

        coordinator.execute(runId);

        assertThat(taskHandler.invocationOrder).containsExactly("extract", "transform", "load");
        assertThat(taskRunStatuses(runId))
                .containsExactlyInAnyOrder(
                        TaskRunStatus.SUCCEEDED, TaskRunStatus.SUCCEEDED, TaskRunStatus.SUCCEEDED);
        assertThat(workflowRunRepository.statusUpdates).endsWith(WorkflowRunStatus.SUCCEEDED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.SUCCEEDED);
    }

    @Test
    void executePassesTaskConfigToHandler() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, new DagDefinition(
                List.of(new TaskDefinition(
                        TaskId.of("task-1"),
                        "Task task-1",
                        "mock",
                        TaskConfig.of(Map.of("success", true, "delayMs", 0)))),
                List.of()));

        coordinator.execute(runId);

        assertThat(taskHandler.receivedInputs).containsExactly(Map.of("success", true, "delayMs", 0));
    }

    @Test
    void executePersistsHandlerOutputOnSuccess() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        taskHandler.succeedWith("computed-result");

        coordinator.execute(runId);

        TaskRun finished = workflowRunRepository.findTaskRunsByRunId(runId).get(0);
        assertThat(finished.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(finished.output()).isEqualTo("computed-result");
    }

    @Test
    void executeDoesNotPersistOutputOnFailure() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        taskHandler.alwaysFail();

        coordinator.execute(runId);

        TaskRun finished = workflowRunRepository.findTaskRunsByRunId(runId).get(0);
        assertThat(finished.status()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(finished.output()).isNull();
        assertThat(finished.errorMessage()).isNotNull();
    }

    @Test
    void executeCascadesSkippedToDependentsWhenRootTaskFailsTerminally() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "extract", TaskRunStatus.PENDING, 0));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "transform", TaskRunStatus.PENDING, 1));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "load", TaskRunStatus.PENDING, 2));
        givenWorkflow(workflowId, linearDag("extract", "transform", "load"));
        taskHandler.alwaysFail();

        coordinator.execute(runId);

        // extract fails terminally (retries exhausted), dependents are skipped, run fails.
        assertThat(taskRunStatuses(runId)).containsExactlyInAnyOrder(
                TaskRunStatus.FAILED, TaskRunStatus.SKIPPED, TaskRunStatus.SKIPPED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.FAILED);
    }

    @Test
    void executeRetriesFailedTaskThenSucceeds() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        // Fail twice (retries 1 and 2), then succeed on the 3rd attempt.
        taskHandler.failThenSucceed(2);

        coordinator.execute(runId);

        assertThat(taskHandler.invocations).hasSize(3);
        assertThat(taskRunStatuses(runId)).containsExactly(TaskRunStatus.SUCCEEDED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.SUCCEEDED);
    }

    @Test
    void executeFailsRunWhenTaskExhaustsRetries() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        taskHandler.alwaysFail();

        coordinator.execute(runId);

        // Initial attempt + 3 retries = 4 executions, then terminal FAILED.
        assertThat(taskHandler.invocations).hasSize(4);
        assertThat(taskRunStatuses(runId)).containsExactly(TaskRunStatus.FAILED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.FAILED);
    }

    @Test
    void executeTreatsHandlerExceptionAsFailureAndRetries() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        taskHandler.alwaysThrow(new RuntimeException("Handler error"));

        coordinator.execute(runId);

        assertThat(taskHandler.invocations).hasSize(4);
        assertThat(taskRunStatuses(runId)).containsExactly(TaskRunStatus.FAILED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.FAILED);
    }

    @Test
    void executeDoesNotRunTaskBeforeItsDependenciesComplete() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        // Pre-skip the root task to simulate a dependency that never succeeds;
        // the dependent must never become runnable and is cascaded to SKIPPED.
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "root", TaskRunStatus.SKIPPED, 0));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "child", TaskRunStatus.PENDING, 1));
        givenWorkflow(workflowId, linearDag("root", "child"));
        taskHandler.alwaysSucceed();

        coordinator.execute(runId);

        assertThat(taskHandler.invocations).isEmpty();
        assertThat(taskRunStatuses(runId)).containsExactlyInAnyOrder(
                TaskRunStatus.SKIPPED, TaskRunStatus.SKIPPED);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.FAILED);
    }

    @Test
    void executeInvokesBackoffBeforeEachRetryWithAscendingAttemptNumber() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        // Fail three retries then succeed: attempts 1,2,3 fail, attempt 4 succeeds.
        taskHandler.failThenSucceed(3);

        coordinator.execute(runId);

        // Backoff is awaited before each retry, with the 1-based attempt number that is
        // about to run (retryCount after increment).
        assertThat(backoffStrategy.awaitedAttempts).containsExactly(1, 2, 3);
        assertThat(taskHandler.invocations).hasSize(4);
        assertThat(workflowRunRepository.findRunById(runId).orElseThrow().status())
                .isEqualTo(WorkflowRunStatus.SUCCEEDED);
    }

    @Test
    void executeDoesNotBackoffWhenTaskSucceedsFirstTime() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        workflowRunRepository.store(createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId));
        workflowRunRepository.storeTaskRun(createTaskRun(runId, "task-1", TaskRunStatus.PENDING, 0));
        givenWorkflow(workflowId, singleTaskDag("task-1"));
        taskHandler.alwaysSucceed();

        coordinator.execute(runId);

        assertThat(backoffStrategy.awaitedAttempts).isEmpty();
    }

    private List<TaskRunStatus> taskRunStatuses(WorkflowRunId runId) {
        return workflowRunRepository.findTaskRunsByRunId(runId).stream()
                .map(TaskRun::status)
                .toList();
    }

    private void givenWorkflow(WorkflowId workflowId, DagDefinition dag) {
        Workflow workflow = Workflow.restore(
                workflowId,
                new WorkflowName("Test Workflow"),
                dag,
                WorkflowStatus.ACTIVE,
                NOW);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
    }

    private static DagDefinition singleTaskDag(String taskId) {
        return new DagDefinition(
                List.of(new TaskDefinition(TaskId.of(taskId), "Task " + taskId, "mock")),
                List.of());
    }

    private static DagDefinition linearDag(String... taskIds) {
        List<TaskDefinition> tasks = new ArrayList<>();
        for (int i = 0; i < taskIds.length; i++) {
            tasks.add(new TaskDefinition(TaskId.of(taskIds[i]), "Task " + taskIds[i], "mock"));
        }
        List<DagEdge> edges = new ArrayList<>();
        for (int i = 0; i < taskIds.length - 1; i++) {
            edges.add(new DagEdge(TaskId.of(taskIds[i]), TaskId.of(taskIds[i + 1])));
        }
        return new DagDefinition(tasks, edges);
    }

    private static WorkflowRun createWorkflowRun(WorkflowRunId runId, WorkflowRunStatus status) {
        return createWorkflowRun(runId, status, WorkflowId.of("workflow-1"));
    }

    private static WorkflowRun createWorkflowRun(WorkflowRunId runId, WorkflowRunStatus status, WorkflowId workflowId) {
        return WorkflowRun.restore(runId, workflowId, status, NOW);
    }

    private static TaskRun createTaskRun(WorkflowRunId runId, String taskId, TaskRunStatus status, int position) {
        return TaskRun.restore(
                TaskRunId.of("run-" + taskId),
                runId,
                TaskId.of(taskId),
                "Task " + taskId,
                "mock",
                status,
                position,
                0,
                3,
                null,
                null,
                null,
                NOW);
    }

    private static ClockPort fixedClock() {
        return () -> NOW;
    }

    /**
     * Stateful in-memory repository that reflects status updates, so multi-wave
     * execution, retries, and skip cascades can be exercised against real state.
     */
    private static final class FakeWorkflowRunRepository implements WorkflowRunRepository {

        private final Map<WorkflowRunId, WorkflowRun> runs = new HashMap<>();
        private final Map<WorkflowRunId, List<TaskRun>> taskRuns = new HashMap<>();
        private final List<WorkflowRunStatus> statusUpdates = new ArrayList<>();

        void store(WorkflowRun run) {
            runs.put(run.id(), run);
            taskRuns.putIfAbsent(run.id(), new ArrayList<>());
        }

        void storeTaskRun(TaskRun taskRun) {
            taskRuns.computeIfAbsent(taskRun.workflowRunId(), k -> new ArrayList<>())
                    .add(taskRun);
        }

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
            runs.put(workflowRun.id(), workflowRun);
            this.taskRuns.put(workflowRun.id(), new ArrayList<>(taskRuns));
            return workflowRun;
        }

        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
            return Optional.ofNullable(runs.get(workflowRunId));
        }

        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
            return List.of();
        }

        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
            return List.copyOf(taskRuns.getOrDefault(workflowRunId, List.of()));
        }

        @Override
        public void updateTaskRunStatus(WorkflowRunId workflowRunId, TaskRun taskRun) {
            List<TaskRun> runs = taskRuns.get(workflowRunId);
            if (runs == null) {
                return;
            }
            for (int i = 0; i < runs.size(); i++) {
                if (runs.get(i).id().equals(taskRun.id())) {
                    runs.set(i, taskRun);
                    return;
                }
            }
        }

        @Override
        public void updateStatus(WorkflowRunId workflowRunId, WorkflowRunStatus status, Instant startedAt) {
            statusUpdates.add(status);
            WorkflowRun existing = runs.get(workflowRunId);
            if (existing == null) {
                return;
            }
            Instant finishedAt = (status == WorkflowRunStatus.SUCCEEDED || status == WorkflowRunStatus.FAILED)
                    ? NOW
                    : existing.finishedAt();
            runs.put(workflowRunId, new WorkflowRun(
                    existing.id(),
                    existing.workflowId(),
                    status,
                    existing.triggeredAt(),
                    startedAt,
                    finishedAt));
        }

        @Override
        public List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
            return List.of();
        }
    }

    /**
     * Task handler whose behavior is configured per test. Records invocation order
     * by task id and supports succeed / fail / fail-then-succeed / throw modes.
     */
    private static final class ConfigurableTaskHandler implements TaskHandler {

        private final List<String> invocations = new ArrayList<>();
        private final List<String> invocationOrder = new ArrayList<>();
        private final List<Map<String, Object>> receivedInputs = new ArrayList<>();
        private Function<TaskRun, TaskResult> behavior = tr -> TaskResult.success("done");

        void alwaysSucceed() {
            behavior = tr -> TaskResult.success("done");
        }

        void succeedWith(String output) {
            behavior = tr -> TaskResult.success(output);
        }

        void alwaysFail() {
            behavior = tr -> TaskResult.failure("MOCK_ERROR", "Mock task failed");
        }

        void alwaysThrow(RuntimeException e) {
            behavior = tr -> {
                throw e;
            };
        }

        void failThenSucceed(int failTimes) {
            behavior = new Function<>() {
                private int remaining = failTimes;

                @Override
                public TaskResult apply(TaskRun taskRun) {
                    if (remaining > 0) {
                        remaining--;
                        return TaskResult.failure("MOCK_ERROR", "Mock task failed");
                    }
                    return TaskResult.success("done");
                }
            };
        }

        @Override
        public String supportedType() {
            return "mock";
        }

        @Override
        public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
            invocations.add(taskRun.taskId().value());
            invocationOrder.add(taskRun.taskId().value());
            receivedInputs.add(Map.copyOf(input));
            return Objects.requireNonNull(behavior.apply(taskRun), "behavior must not return null");
        }
    }

    /**
     * Backoff strategy that records each awaited attempt without sleeping, so retry
     * backoff timing is testable without real delays.
     */
    private static final class RecordingBackoffStrategy implements BackoffStrategy {

        private final List<Integer> awaitedAttempts = new ArrayList<>();

        @Override
        public void awaitRetry(int attemptNumber) {
            awaitedAttempts.add(attemptNumber);
        }
    }
}
