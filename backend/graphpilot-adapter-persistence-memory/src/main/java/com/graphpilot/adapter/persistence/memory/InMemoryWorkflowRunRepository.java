package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryWorkflowRunRepository implements WorkflowRunRepository {

    private final ConcurrentMap<WorkflowRunId, WorkflowRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentMap<WorkflowRunId, List<TaskRun>> taskRuns = new ConcurrentHashMap<>();

    @Override
    public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
        Objects.requireNonNull(workflowRun, "workflowRun must not be null");
        Objects.requireNonNull(taskRuns, "taskRuns must not be null");
        this.taskRuns.put(workflowRun.id(), List.copyOf(taskRuns));
        runs.put(workflowRun.id(), workflowRun);
        return workflowRun;
    }

    @Override
    public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        return Optional.ofNullable(runs.get(workflowRunId));
    }

    @Override
    public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Workflow run query limit must be positive");
        }
        return runs.values().stream()
                .filter(workflowRun -> workflowRun.workflowId().equals(workflowId))
                .sorted(Comparator
                        .comparing(WorkflowRun::triggeredAt)
                        .thenComparing(workflowRun -> workflowRun.id().value()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        return taskRuns.getOrDefault(workflowRunId, List.of()).stream()
                .sorted(Comparator
                        .comparingInt(TaskRun::position)
                        .thenComparing(taskRun -> taskRun.taskId().value()))
                .toList();
    }
}
