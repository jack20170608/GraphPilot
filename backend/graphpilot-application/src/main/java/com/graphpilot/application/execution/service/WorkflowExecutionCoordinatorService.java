package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.TaskRunRepository;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.Workflow;
import java.time.Instant;
import java.util.HashSet;
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
    private final TaskRunRepository taskRunRepository;
    private final TaskHandlerProvider taskHandlerProvider;
    private final ClockPort clock;

    public WorkflowExecutionCoordinatorService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskRunRepository taskRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.workflowRunRepository = Objects.requireNonNull(workflowRunRepository, "workflowRunId must not be null");
        this.taskRunRepository = Objects.requireNonNull(taskRunRepository, "taskRunRepository must not be null");
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
        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(workflowRunId);
        Map<TaskId, TaskRun> taskRunsByTaskId = taskRuns.stream()
                .collect(Collectors.toMap(TaskRun::taskId, Function.identity()));

        // Find runnable tasks (dependencies satisfied)
        Set<TaskId> completedTaskIds = taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.SUCCEEDED)
                .map(TaskRun::taskId)
                .collect(Collectors.toSet());

        List<TaskRun> runnableTasks = findRunnableTasks(taskRuns, taskDefsById, completedTaskIds);

        // Execute each runnable task
        for (TaskRun taskRun : runnableTasks) {
            executeTask(taskRun, taskDefsById.get(taskRun.taskId()), now);
        }

        // Check if workflow is complete
        checkWorkflowCompletion(workflowRunId, taskRuns);
    }

    private List<TaskRun> findRunnableTasks(
            List<TaskRun> taskRuns,
            Map<TaskId, TaskDefinition> taskDefsById,
            Set<TaskId> completedTaskIds) {

        return taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.PENDING)
                .filter(tr -> {
                    // Get edges where this task is the target (dependencies)
                    TaskDefinition taskDef = taskDefsById.get(tr.taskId());
                    if (taskDef == null) {
                        return false;
                    }
                    // For MVP: simple topological order by position
                    // In advanced version, would check actual DAG edges
                    return true;
                })
                .sorted((a, b) -> Integer.compare(a.position(), b.position()))
                .toList();
    }

    private void executeTask(TaskRun taskRun, TaskDefinition taskDef, Instant defaultStartedAt) {
        TaskHandler handler = taskHandlerProvider.getHandler(taskRun.taskType());

        // Mark as RUNNING
        Instant startedAt = taskRun.startedAt() != null ? taskRun.startedAt() : defaultStartedAt;
        taskRunRepository.updateStatus(
                taskRun.id(),
                TaskRunStatus.RUNNING,
                null,
                startedAt,
                null,
                taskRun.retryCount());

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
            // Will retry - don't mark as failed yet
            newRetryCount = taskRun.retryCount() + 1;
            newStatus = TaskRunStatus.PENDING;
            taskRunRepository.updateStatus(taskRun.id(), newStatus, null, null, null, newRetryCount);
        } else {
            taskRunRepository.updateStatus(taskRun.id(), newStatus, errorMessage, startedAt, now, newRetryCount);
        }
    }

    private void checkWorkflowCompletion(WorkflowRunId workflowRunId, List<TaskRun> taskRuns) {
        long completed = taskRuns.stream()
                .filter(tr -> tr.status() == TaskRunStatus.SUCCEEDED
                           || tr.status() == TaskRunStatus.FAILED
                           || tr.status() == TaskRunStatus.SKIPPED)
                .count();

        Instant now = clock.now();
        if (completed == taskRuns.size()) {
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