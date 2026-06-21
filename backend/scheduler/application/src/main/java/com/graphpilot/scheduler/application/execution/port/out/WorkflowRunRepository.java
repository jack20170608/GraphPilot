package com.graphpilot.scheduler.application.execution.port.out;

import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.List;
import java.util.Optional;

/**
 * Port for workflow run persistence.
 */
public interface WorkflowRunRepository {

    WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns);

    Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId);

    List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit);

    List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);

    /**
     * Update a task run status with execution result.
     * Default implementation throws UnsupportedOperationException.
     */
    default void updateTaskRunStatus(WorkflowRunId workflowRunId, TaskRun taskRun) {
        throw new UnsupportedOperationException("updateTaskRunStatus not implemented");
    }

    /**
     * Update workflow run status and timestamps.
     * Default implementation throws UnsupportedOperationException.
     */
    default void updateStatus(WorkflowRunId workflowRunId, WorkflowRunStatus status, java.time.Instant startedAt) {
        throw new UnsupportedOperationException("updateStatus not implemented");
    }

    /**
     * Find workflow runs with specific status.
     * Default implementation returns empty list.
     */
    default List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
        return List.of();
    }
}