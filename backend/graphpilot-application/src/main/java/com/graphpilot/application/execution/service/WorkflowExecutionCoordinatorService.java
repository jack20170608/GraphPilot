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

    public WorkflowExecutionCoordinatorService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.workflowRunRepository = Objects.requireNonNull(workflowRunRepository, "workflowRunRepository must not be null");
        this.taskHandlerProvider = Objects.requireNonNull(taskHandlerProvider, "taskHandlerProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        var workflowId = workflowRun.workflowId();
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalStateException("Workflow not found: " + workflowId));

        DagDefinition dag = workflow.dag();
        Map<TaskId, TaskDefinition> taskDefsById = dag.tasks().stream()
                .collect(Collectors.toMap(TaskDefinition::id, Function.identity()));

        // Mark workflow as RUNNING if first time
        Instant now = clock.now();
        if (workflowRun.status() == WorkflowRunStatus.PENDING) {
            updateWorkflowRunStatus(workflowRunId, WorkflowRunStatus.RUNNING, now);
            workflowRun = workflowRun.withStatus(WorkflowRunStatus.RUNNING).withStartedAt(now);
        }

        // Get task runs
        List<TaskRun> taskRuns = workflowRunRepository.findTaskRunsByRunId(workflowRunId);

        // Find runnable tasks (dependencies satisfied)
        Set<TaskId> completedTaskIds = taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.SUCCEEDED)
                .map(TaskRun::taskId)
                .collect(Collectors.toSet());

        List<TaskRun> runnableTasks = findRunnableTasks(taskRuns, taskDefsById, completedTaskIds, dag);

        // Execute each runnable task
        for (TaskRun taskRun : runnableTasks) {
            executeTask(taskRun, taskDefsById.get(taskRun.taskId()), now);
        }

        // Check if workflow is complete (reload task runs after execution)
        List<TaskRun> updatedTaskRuns = workflowRunRepository.findTaskRunsByRunId(workflowRunId);
        checkWorkflowCompletion(workflowRunId, updatedTaskRuns);
    }

    private List<TaskRun> findRunnableTasks(
            List<TaskRun> taskRuns,
            Map<TaskId, TaskDefinition> taskDefsById,
            Set<TaskId> completedTaskIds,
            DagDefinition dag) {

        // Build dependency map from DAG edges
        Map<TaskId, Set<TaskId>> dependencies = new java.util.HashMap<>();
        for (DagEdge edge : dag.edges()) {
            dependencies.computeIfAbsent(edge.toTaskId(), k -> new java.util.HashSet<>())
                    .add(edge.fromTaskId());
        }

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

        // Update status based on result
        Instant now = clock.now();
        String errorMessage = result.isFailure() ?
                result.error().orElse(result.errorMessage().orElse("Unknown error")) : null;

        TaskRunStatus newStatus = result.status();
        int newRetryCount = taskRun.retryCount();

        if (newStatus == TaskRunStatus.FAILED && taskRun.canRetry()) {
            // Will retry
            newRetryCount = taskRun.retryCount() + 1;
            newStatus = TaskRunStatus.PENDING;
            TaskRun retryTaskRun = taskRun.withStatus(newStatus).withIncrementedRetry();
            workflowRunRepository.updateTaskRunStatus(taskRun.workflowRunId(), retryTaskRun);
        } else {
            TaskRun finishedTaskRun = taskRun.withStatus(newStatus)
                    .withStartedAt(startedAt)
                    .withFinishedAt(now)
                    .withErrorMessage(errorMessage);
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