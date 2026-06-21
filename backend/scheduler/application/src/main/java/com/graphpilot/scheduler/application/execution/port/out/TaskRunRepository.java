package com.graphpilot.scheduler.application.execution.port.out;

import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.List;

/**
 * Port for task run persistence and status updates.
 */
public interface TaskRunRepository {

    /**
     * Find all task runs for a workflow run.
     */
    List<TaskRun> findByWorkflowRunId(WorkflowRunId workflowRunId);

    /**
     * Find pending task runs that can be executed (dependencies satisfied).
     * For MVP, returns all PENDING tasks ordered by position.
     */
    List<TaskRun> findPendingTasks(WorkflowRunId workflowRunId);

    /**
     * Find a task run by ID.
     */
    TaskRun findById(TaskRunId taskRunId);

    /**
     * Update task run status with execution result.
     */
    void updateStatus(TaskRunId taskRunId, TaskRunStatus status, String errorMessage,
            String output, java.time.Instant startedAt, java.time.Instant finishedAt, int retryCount);

    /**
     * Count completed (SUCCEEDED or FAILED) tasks for a workflow run.
     */
    int countCompleted(WorkflowRunId workflowRunId);

    /**
     * Count total tasks for a workflow run.
     */
    int countTotal(WorkflowRunId workflowRunId);
}