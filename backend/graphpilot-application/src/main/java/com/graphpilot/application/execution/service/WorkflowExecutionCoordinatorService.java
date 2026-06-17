package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.Workflow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates workflow execution by processing tasks in DAG order.
 * Handles task execution, status updates, retry logic, and workflow completion.
 */
public final class WorkflowExecutionCoordinatorService implements ExecuteWorkflowRunUseCase {

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final TaskHandlerProvider taskHandlerProvider;
    private final ClockPort clock;
    private final BackoffStrategy backoffStrategy;

    public WorkflowExecutionCoordinatorService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock) {
        this(workflowRepository, workflowRunRepository, taskHandlerProvider, clock, attemptNumber -> { });
    }

    public WorkflowExecutionCoordinatorService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock,
            BackoffStrategy backoffStrategy) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.workflowRunRepository = Objects.requireNonNull(workflowRunRepository, "workflowRunRepository must not be null");
        this.taskHandlerProvider = Objects.requireNonNull(taskHandlerProvider, "taskHandlerProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy must not be null");
    }

    @Override
    public void execute(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");

        // Load workflow run
        WorkflowRun workflowRun = workflowRunRepository.findRunById(workflowRunId)
                .orElseThrow(() -> new IllegalStateException("Workflow run not found: " + workflowRunId));

        // Skip if already completed
        if (workflowRun.status() == WorkflowRunStatus.SUCCEEDED
                || workflowRun.status() == WorkflowRunStatus.FAILED) {
            return;
        }

        // Load workflow definition
        Workflow workflow = workflowRepository.findById(workflowRun.workflowId())
                .orElseThrow(() -> new IllegalStateException("Workflow not found: " + workflowRun.workflowId()));

        DagDefinition dag = workflow.dag();
        Map<TaskId, TaskDefinition> taskDefsById = dag.tasks().stream()
                .collect(Collectors.toMap(TaskDefinition::id, Function.identity()));
        Map<TaskId, Set<TaskId>> dependencies = buildDependencyMap(dag);

        // Mark workflow as RUNNING if first time
        if (workflowRun.status() == WorkflowRunStatus.PENDING) {
            updateWorkflowRunStatus(workflowRunId, WorkflowRunStatus.RUNNING, clock.now());
        }

        // Wave-based execution loop: repeatedly execute runnable tasks and cascade
        // skips until no further progress can be made, then finalize the run status.
        // Reloading task runs each wave lets the DAG advance past root tasks and lets
        // retried (PENDING) tasks be re-executed, all driven by a single event.
        while (true) {
            List<TaskRun> taskRuns = workflowRunRepository.findTaskRunsByRunId(workflowRunId);
            Set<TaskId> completedTaskIds = taskRuns.stream()
                    .filter(tr -> tr.status() == TaskRunStatus.SUCCEEDED)
                    .map(TaskRun::taskId)
                    .collect(Collectors.toSet());

            List<TaskRun> runnableTasks = findRunnableTasks(taskRuns, taskDefsById, completedTaskIds, dependencies);
            if (!runnableTasks.isEmpty()) {
                Instant executionTime = clock.now();
                for (TaskRun taskRun : runnableTasks) {
                    executeTask(taskRun, taskDefsById.get(taskRun.taskId()), executionTime);
                }
                continue;
            }

            // No runnable tasks: cascade SKIPPED to tasks blocked by failed/skipped
            // dependencies so the run can still reach a terminal state.
            if (cascadeSkippedTasks(workflowRunId, taskRuns, dependencies)) {
                continue;
            }

            break;
        }

        // Finalize workflow run status now that no task is PENDING.
        List<TaskRun> finalTaskRuns = workflowRunRepository.findTaskRunsByRunId(workflowRunId);
        checkWorkflowCompletion(workflowRunId, finalTaskRuns);
    }

    private Map<TaskId, Set<TaskId>> buildDependencyMap(DagDefinition dag) {
        Map<TaskId, Set<TaskId>> dependencies = new java.util.HashMap<>();
        for (DagEdge edge : dag.edges()) {
            dependencies.computeIfAbsent(edge.toTaskId(), k -> new java.util.HashSet<>())
                    .add(edge.fromTaskId());
        }
        return dependencies;
    }

    private List<TaskRun> findRunnableTasks(
            List<TaskRun> taskRuns,
            Map<TaskId, TaskDefinition> taskDefsById,
            Set<TaskId> completedTaskIds,
            Map<TaskId, Set<TaskId>> dependencies) {

        return taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.PENDING)
                .filter(tr -> {
                    TaskDefinition taskDef = taskDefsById.get(tr.taskId());
                    if (taskDef == null) {
                        return false;
                    }
                    // Check if all dependencies are satisfied
                    Set<TaskId> deps = dependencies.get(tr.taskId());
                    if (deps == null || deps.isEmpty()) {
                        return true; // No dependencies, can run immediately
                    }
                    // All dependencies must be completed
                    return completedTaskIds.containsAll(deps);
                })
                .sorted((a, b) -> Integer.compare(a.position(), b.position()))
                .toList();
    }

    private boolean cascadeSkippedTasks(
            WorkflowRunId workflowRunId,
            List<TaskRun> taskRuns,
            Map<TaskId, Set<TaskId>> dependencies) {
        Set<TaskId> failedOrSkipped = taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.FAILED
                        || tr.status() == TaskRunStatus.SKIPPED)
                .map(TaskRun::taskId)
                .collect(Collectors.toSet());

        boolean anySkipped = false;
        for (TaskRun taskRun : taskRuns) {
            if (taskRun.status() != TaskRunStatus.PENDING) {
                continue;
            }
            Set<TaskId> deps = dependencies.get(taskRun.taskId());
            if (deps == null || deps.isEmpty()) {
                continue;
            }
            if (deps.stream().anyMatch(failedOrSkipped::contains)) {
                TaskRun skipped = taskRun.withStatus(TaskRunStatus.SKIPPED)
                        .withFinishedAt(clock.now());
                workflowRunRepository.updateTaskRunStatus(workflowRunId, skipped);
                anySkipped = true;
            }
        }
        return anySkipped;
    }

    private void executeTask(TaskRun taskRun, TaskDefinition taskDef, Instant defaultStartedAt) {
        TaskHandler handler = taskHandlerProvider.getHandler(taskRun.taskType());

        // Mark as RUNNING
        Instant startedAt = taskRun.startedAt() != null ? taskRun.startedAt() : defaultStartedAt;
        workflowRunRepository.updateTaskRunStatus(taskRun.workflowRunId(),
                taskRun.withStatus(TaskRunStatus.RUNNING).withStartedAt(startedAt));

        TaskResult result;
        try {
            result = handler.execute(taskRun, taskDef, Map.of());
        } catch (Exception e) {
            result = TaskResult.failure(e.getClass().getSimpleName(), e.getMessage());
        }

        // Project the post-execution task run, then decide whether to retry. canRetry()
        // must be evaluated on the FAILED view: the incoming taskRun is still PENDING, so
        // asking it directly would never allow a retry.
        Instant now = clock.now();
        String errorMessage = result.isFailure()
                ? result.error().orElse(result.errorMessage().orElse("Unknown error"))
                : null;
        String output = result.isSuccess() ? result.output().orElse(null) : null;
        TaskRun finishedTaskRun = taskRun.withStatus(result.status())
                .withStartedAt(startedAt)
                .withFinishedAt(now)
                .withErrorMessage(errorMessage)
                .withOutput(output);

        if (result.isFailure() && finishedTaskRun.canRetry()) {
            // Back off before the retry, then reset to PENDING for re-execution in a
            // later wave. retryCount is incremented so retries are bounded by maxRetries;
            // the new retryCount is the 1-based attempt number about to run.
            TaskRun retried = finishedTaskRun.withIncrementedRetry();
            backoffStrategy.awaitRetry(retried.retryCount());
            workflowRunRepository.updateTaskRunStatus(taskRun.workflowRunId(), retried);
        } else {
            workflowRunRepository.updateTaskRunStatus(taskRun.workflowRunId(), finishedTaskRun);
        }
    }

    private void checkWorkflowCompletion(WorkflowRunId workflowRunId, List<TaskRun> taskRuns) {
        long completed = taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.SUCCEEDED
                           || tr.status() == TaskRunStatus.FAILED
                           || tr.status() == TaskRunStatus.SKIPPED)
                .count();

        if (completed == taskRuns.size()) {
            Instant now = clock.now();
            boolean allSucceeded = taskRuns.stream()
                    .allMatch(tr -> tr.status() == TaskRunStatus.SUCCEEDED);
            WorkflowRunStatus finalStatus = allSucceeded ?
                    WorkflowRunStatus.SUCCEEDED : WorkflowRunStatus.FAILED;
            updateWorkflowRunStatus(workflowRunId, finalStatus, now);
        }
    }

    private void updateWorkflowRunStatus(WorkflowRunId workflowRunId, WorkflowRunStatus status, Instant now) {
        workflowRunRepository.updateStatus(workflowRunId, status, now);
    }
}