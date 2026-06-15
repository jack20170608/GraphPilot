package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskResult;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowExecutionCoordinatorServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowRunRepository workflowRunRepository;
    @Mock
    private TaskHandlerProvider taskHandlerProvider;
    @Mock
    private ClockPort clock;
    @Mock
    private TaskHandler taskHandler;

    private WorkflowExecutionCoordinatorService coordinator;
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    @BeforeEach
    void setUp() {
        coordinator = new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                clock);
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void executeThrowsForNullWorkflowRunId() {
        assertThatThrownBy(() -> coordinator.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executeThrowsWhenWorkflowRunNotFound() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coordinator.execute(runId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow run not found");
    }

    @Test
    void executeSkipsWhenAlreadySucceeded() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowRun completedRun = createWorkflowRun(runId, WorkflowRunStatus.SUCCEEDED);
        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(completedRun));

        coordinator.execute(runId);

        verify(workflowRunRepository, never()).findTaskRunsByRunId(any());
    }

    @Test
    void executeSkipsWhenAlreadyFailed() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowRun completedRun = createWorkflowRun(runId, WorkflowRunStatus.FAILED);
        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(completedRun));

        coordinator.execute(runId);

        verify(workflowRunRepository, never()).findTaskRunsByRunId(any());
    }

    @Test
    void executeMarksPendingWorkflowAsRunning() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun pendingRun = createWorkflowRun(runId, WorkflowRunStatus.PENDING, workflowId);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(pendingRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(createWorkflow(workflowId)));
        when(workflowRunRepository.findTaskRunsByRunId(runId)).thenReturn(List.of());
        when(taskHandlerProvider.getHandler(any())).thenReturn(taskHandler);

        coordinator.execute(runId);

        verify(workflowRunRepository).updateStatus(eq(runId), eq(WorkflowRunStatus.RUNNING), eq(NOW));
    }

    @Test
    void executeOnlyRunsTasksWithSatisfiedDependencies() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun runningRun = createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId);

        // task-1 -> task-2 (task-2 depends on task-1)
        DagDefinition dagWithDeps = new DagDefinition(
                List.of(
                        new TaskDefinition(TaskId.of("task-1"), "Task 1", "mock"),
                        new TaskDefinition(TaskId.of("task-2"), "Task 2", "mock")),
                List.of(new DagEdge(TaskId.of("task-1"), TaskId.of("task-2"))));

        Workflow workflowWithDeps = Workflow.restore(
                workflowId,
                new WorkflowName("Test Workflow"),
                dagWithDeps,
                WorkflowStatus.DRAFT,
                NOW);

        // Both tasks are PENDING
        TaskRun task1Pending = createTaskRun("task-1", TaskRunStatus.PENDING, 0);
        TaskRun task2Pending = createTaskRun("task-2", TaskRunStatus.PENDING, 1);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(runningRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflowWithDeps));
        // First call: find runnable tasks, return both
        // Second call: after task-1 executed, check completion, return both (task-1 succeeded, task-2 still pending)
        when(workflowRunRepository.findTaskRunsByRunId(runId))
                .thenReturn(List.of(task1Pending, task2Pending))
                .thenReturn(List.of(task1Pending, task2Pending));
        when(taskHandlerProvider.getHandler("mock")).thenReturn(taskHandler);
        when(taskHandler.execute(any(), any(), any())).thenReturn(TaskResult.success("done"));

        coordinator.execute(runId);

        // Only task-1 should be executed (task-2 depends on task-1 which hasn't completed yet)
        verify(taskHandler, times(1)).execute(any(), any(), any());
    }

    @Test
    void executeRunsTasksWhenDependenciesAreCompleted() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun runningRun = createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId);

        // task-1 -> task-2
        DagDefinition dagWithDeps = new DagDefinition(
                List.of(
                        new TaskDefinition(TaskId.of("task-1"), "Task 1", "mock"),
                        new TaskDefinition(TaskId.of("task-2"), "Task 2", "mock")),
                List.of(new DagEdge(TaskId.of("task-1"), TaskId.of("task-2"))));

        Workflow workflowWithDeps = Workflow.restore(
                workflowId,
                new WorkflowName("Test Workflow"),
                dagWithDeps,
                WorkflowStatus.DRAFT,
                NOW);

        // task-1 already SUCCEEDED, task-2 is PENDING
        TaskRun task1Succeeded = createTaskRun("task-1", TaskRunStatus.SUCCEEDED, 0);
        TaskRun task2Pending = createTaskRun("task-2", TaskRunStatus.PENDING, 1);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(runningRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflowWithDeps));
        when(workflowRunRepository.findTaskRunsByRunId(runId))
                .thenReturn(List.of(task1Succeeded, task2Pending))
                .thenReturn(List.of(task1Succeeded, task2Pending));
        when(taskHandlerProvider.getHandler("mock")).thenReturn(taskHandler);
        when(taskHandler.execute(any(), any(), any())).thenReturn(TaskResult.success("done"));

        coordinator.execute(runId);

        // task-2 should be executed now (dependency task-1 is SUCCEEDED)
        verify(taskHandler, times(1)).execute(any(), any(), any());
    }

    @Test
    void executeUpdatesWorkflowStatusToSucceededWhenAllTasksComplete() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun runningRun = createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId);
        TaskRun succeededTask = createTaskRun("task-1", TaskRunStatus.SUCCEEDED, 0);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(runningRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(createWorkflow(workflowId)));
        when(workflowRunRepository.findTaskRunsByRunId(runId)).thenReturn(List.of(succeededTask));
        when(taskHandlerProvider.getHandler(any())).thenReturn(taskHandler);

        coordinator.execute(runId);

        verify(workflowRunRepository).updateStatus(eq(runId), eq(WorkflowRunStatus.SUCCEEDED), any());
    }

    @Test
    void executeUpdatesWorkflowStatusToFailedWhenAnyTaskFails() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun runningRun = createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId);
        TaskRun succeededTask = createTaskRun("task-1", TaskRunStatus.SUCCEEDED, 0);
        TaskRun failedTask = createTaskRun("task-2", TaskRunStatus.FAILED, 1);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(runningRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(createWorkflow(workflowId)));
        when(workflowRunRepository.findTaskRunsByRunId(runId)).thenReturn(List.of(succeededTask, failedTask));
        when(taskHandlerProvider.getHandler(any())).thenReturn(taskHandler);

        coordinator.execute(runId);

        verify(workflowRunRepository).updateStatus(eq(runId), eq(WorkflowRunStatus.FAILED), any());
    }

    @Test
    void executeHandlesTaskHandlerFailureWithoutThrowing() {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRun runningRun = createWorkflowRun(runId, WorkflowRunStatus.RUNNING, workflowId);
        TaskRun pendingTask = createTaskRun("task-1", TaskRunStatus.PENDING, 0);

        when(workflowRunRepository.findRunById(runId)).thenReturn(Optional.of(runningRun));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(createWorkflow(workflowId)));
        when(workflowRunRepository.findTaskRunsByRunId(runId)).thenReturn(List.of(pendingTask));
        when(taskHandlerProvider.getHandler("mock")).thenReturn(taskHandler);
        // Handler throws exception
        when(taskHandler.execute(any(), any(), any())).thenThrow(new RuntimeException("Handler error"));

        // Should not throw
        coordinator.execute(runId);

        // Verify that at least task status was attempted to be updated
        verify(workflowRunRepository, atLeastOnce()).updateTaskRunStatus(eq(runId), any());
    }

    private WorkflowRun createWorkflowRun(WorkflowRunId runId, WorkflowRunStatus status) {
        return createWorkflowRun(runId, status, WorkflowId.of("workflow-1"));
    }

    private WorkflowRun createWorkflowRun(WorkflowRunId runId, WorkflowRunStatus status, WorkflowId workflowId) {
        return WorkflowRun.restore(runId, workflowId, status, NOW);
    }

    private Workflow createWorkflow(WorkflowId workflowId) {
        DagDefinition dag = new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("task-1"), "Task 1", "mock")),
                List.of());
        return Workflow.restore(
                workflowId,
                new WorkflowName("Test Workflow"),
                dag,
                WorkflowStatus.DRAFT,
                NOW);
    }

    private TaskRun createTaskRun(String taskId, TaskRunStatus status, int position) {
        return TaskRun.restore(
                com.graphpilot.domain.execution.TaskRunId.of("run-" + taskId),
                WorkflowRunId.of("run-1"),
                TaskId.of(taskId),
                "Task " + taskId,
                status,
                position,
                NOW);
    }
}