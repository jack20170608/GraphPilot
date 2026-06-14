package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunMapper;
import com.graphpilot.application.execution.port.out.TaskRunRepository;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRunId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisTaskRunRepository implements TaskRunRepository {

    private final WorkflowRunMapper workflowRunMapper;

    public MyBatisTaskRunRepository(WorkflowRunMapper workflowRunMapper) {
        this.workflowRunMapper = Objects.requireNonNull(workflowRunMapper, "workflowRunMapper must not be null");
    }

    @Override
    public List<TaskRun> findByWorkflowRunId(WorkflowRunId workflowRunId) {
        return workflowRunMapper.findTaskRunsByRunId(workflowRunId.value()).stream()
                .map(this::toTaskRun)
                .toList();
    }

    @Override
    public List<TaskRun> findPendingTasks(WorkflowRunId workflowRunId) {
        return workflowRunMapper.findPendingTaskRunsByRunId(workflowRunId.value()).stream()
                .map(this::toTaskRun)
                .toList();
    }

    @Override
    public TaskRun findById(TaskRunId taskRunId) {
        // This would need a new query method or reuse existing
        // For now, we'll search through all tasks (not efficient but works for MVP)
        List<TaskRun> allTasks = workflowRunMapper.findTaskRunsByRunId(
                taskRunId.value().split("-")[0] // This is a hack - proper impl needs findById
        ).stream().map(this::toTaskRun).toList();

        return allTasks.stream()
                .filter(tr -> tr.id().equals(taskRunId))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public void updateStatus(TaskRunId taskRunId, TaskRunStatus status, String errorMessage,
            Instant startedAt, Instant finishedAt, int retryCount) {
        workflowRunMapper.updateTaskRunStatus(
                taskRunId.value(),
                status.name(),
                errorMessage,
                startedAt,
                finishedAt,
                retryCount);
    }

    @Override
    @Transactional(readOnly = true)
    public int countCompleted(WorkflowRunId workflowRunId) {
        return (int) workflowRunMapper.findTaskRunsByRunId(workflowRunId.value()).stream()
                .filter(row -> {
                    String status = row.status();
                    return "SUCCEEDED".equals(status) || "FAILED".equals(status);
                })
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public int countTotal(WorkflowRunId workflowRunId) {
        return workflowRunMapper.findTaskRunsByRunId(workflowRunId.value()).size();
    }

    private TaskRun toTaskRun(com.graphpilot.adapter.persistence.mybatis.row.TaskRunRow row) {
        return TaskRun.restore(
                TaskRunId.of(row.id()),
                WorkflowRunId.of(row.workflowRunId()),
                TaskId.of(row.taskId()),
                row.taskName(),
                TaskRunStatus.valueOf(row.status()),
                row.position(),
                row.createdAt());
    }
}