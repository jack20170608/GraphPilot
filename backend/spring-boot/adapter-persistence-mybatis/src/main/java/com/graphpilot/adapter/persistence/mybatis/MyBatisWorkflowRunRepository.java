package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunMapper;
import com.graphpilot.adapter.persistence.mybatis.row.TaskRunRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRunRow;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisWorkflowRunRepository implements WorkflowRunRepository {

    private static final String POSITIVE_LIMIT_REQUIRED = "Workflow run query limit must be positive";

    private final WorkflowRunMapper workflowRunMapper;

    public MyBatisWorkflowRunRepository(WorkflowRunMapper workflowRunMapper) {
        this.workflowRunMapper = Objects.requireNonNull(workflowRunMapper, "workflowRunMapper must not be null");
    }

    @Override
    @Transactional
    public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
        Objects.requireNonNull(workflowRun, "workflowRun must not be null");
        Objects.requireNonNull(taskRuns, "taskRuns must not be null");

        validateTaskRunOwnership(workflowRun.id(), taskRuns);

        workflowRunMapper.insertWorkflowRun(toWorkflowRunRow(workflowRun));
        List<TaskRunRow> taskRunRows = taskRuns.stream()
                .map(MyBatisWorkflowRunRepository::toTaskRunRow)
                .toList();
        if (!taskRunRows.isEmpty()) {
            workflowRunMapper.insertTaskRuns(taskRunRows);
        }
        return workflowRun;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");

        return Optional.ofNullable(workflowRunMapper.findWorkflowRunById(workflowRunId.value()))
                .map(MyBatisWorkflowRunRepository::toWorkflowRun);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException(POSITIVE_LIMIT_REQUIRED);
        }

        return workflowRunMapper.findWorkflowRunsByWorkflowId(workflowId.value(), limit).stream()
                .map(MyBatisWorkflowRunRepository::toWorkflowRun)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");

        return workflowRunMapper.findTaskRunsByRunId(workflowRunId.value()).stream()
                .map(MyBatisWorkflowRunRepository::toTaskRun)
                .toList();
    }

    @Override
    @Transactional
    public void updateTaskRunStatus(WorkflowRunId workflowRunId, TaskRun taskRun) {
        Objects.requireNonNull(taskRun, "taskRun must not be null");
        workflowRunMapper.updateTaskRunStatus(
                taskRun.id().value(),
                taskRun.status().name(),
                taskRun.errorMessage(),
                taskRun.output(),
                taskRun.startedAt(),
                taskRun.finishedAt(),
                taskRun.retryCount());
    }

    @Override
    @Transactional
    public void updateStatus(WorkflowRunId workflowRunId, WorkflowRunStatus status, java.time.Instant startedAt) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        Instant finishedAt = (status == WorkflowRunStatus.SUCCEEDED || status == WorkflowRunStatus.FAILED)
                ? Instant.now() : null;
        workflowRunMapper.updateWorkflowRunStatus(
                workflowRunId.value(),
                status.name(),
                startedAt,
                finishedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
        Objects.requireNonNull(status, "status must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Workflow run query limit must be positive");
        }
        return workflowRunMapper.findWorkflowRunsByStatus(status.name(), limit).stream()
                .map(MyBatisWorkflowRunRepository::toWorkflowRun)
                .toList();
    }

    private static void validateTaskRunOwnership(WorkflowRunId workflowRunId, List<TaskRun> taskRuns) {
        for (TaskRun taskRun : taskRuns) {
            if (taskRun == null) {
                throw new IllegalArgumentException("taskRun must not be null");
            }
            if (!taskRun.workflowRunId().equals(workflowRunId)) {
                throw new IllegalArgumentException("Task run workflowRunId must match workflow run id");
            }
        }
    }

    private static WorkflowRunRow toWorkflowRunRow(WorkflowRun workflowRun) {
        return WorkflowRunRow.of(
                workflowRun.id().value(),
                workflowRun.workflowId().value(),
                workflowRun.status().name(),
                workflowRun.triggeredAt());
    }

    private static TaskRunRow toTaskRunRow(TaskRun taskRun) {
        return new TaskRunRow(
                taskRun.id().value(),
                taskRun.workflowRunId().value(),
                taskRun.taskId().value(),
                taskRun.taskName(),
                taskRun.taskType(),
                taskRun.status().name(),
                taskRun.position(),
                taskRun.retryCount(),
                taskRun.maxRetries(),
                taskRun.errorMessage(),
                taskRun.output(),
                taskRun.startedAt(),
                taskRun.finishedAt(),
                taskRun.createdAt());
    }

    private static WorkflowRun toWorkflowRun(WorkflowRunRow workflowRunRow) {
        return WorkflowRun.restore(
                WorkflowRunId.of(workflowRunRow.id()),
                WorkflowId.of(workflowRunRow.workflowId()),
                WorkflowRunStatus.valueOf(workflowRunRow.status()),
                workflowRunRow.triggeredAt());
    }

    private static TaskRun toTaskRun(TaskRunRow taskRunRow) {
        return TaskRun.restore(
                TaskRunId.of(taskRunRow.id()),
                WorkflowRunId.of(taskRunRow.workflowRunId()),
                TaskId.of(taskRunRow.taskId()),
                taskRunRow.taskName(),
                taskRunRow.taskType(),
                TaskRunStatus.valueOf(taskRunRow.status()),
                taskRunRow.position(),
                taskRunRow.retryCount(),
                taskRunRow.maxRetries(),
                taskRunRow.errorMessage(),
                taskRunRow.output(),
                taskRunRow.startedAt(),
                taskRunRow.finishedAt(),
                taskRunRow.createdAt());
    }
}
