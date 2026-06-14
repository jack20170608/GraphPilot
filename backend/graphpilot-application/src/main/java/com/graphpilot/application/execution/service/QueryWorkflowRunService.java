package com.graphpilot.application.execution.service;

import com.graphpilot.application.execution.WorkflowRunNotFoundException;
import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.List;
import java.util.Objects;

public final class QueryWorkflowRunService implements QueryWorkflowRunUseCase {

    private final WorkflowRunRepository workflowRunRepository;

    public QueryWorkflowRunService(WorkflowRunRepository workflowRunRepository) {
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository,
                "workflowRunRepository must not be null");
    }

    @Override
    public WorkflowRun findRunById(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        return workflowRunRepository.findRunById(workflowRunId)
                .orElseThrow(() -> new WorkflowRunNotFoundException(workflowRunId));
    }

    @Override
    public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        validateLimit(limit);
        return List.copyOf(workflowRunRepository.findRunsByWorkflowId(workflowId, limit));
    }

    @Override
    public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
        findRunById(workflowRunId);
        return List.copyOf(workflowRunRepository.findTaskRunsByRunId(workflowRunId));
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Workflow run query limit must be positive");
        }
    }
}
