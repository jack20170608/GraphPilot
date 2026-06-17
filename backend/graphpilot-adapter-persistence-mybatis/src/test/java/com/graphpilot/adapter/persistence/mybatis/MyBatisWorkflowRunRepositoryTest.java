package com.graphpilot.adapter.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunMapper;
import com.graphpilot.adapter.persistence.mybatis.row.TaskRunRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRunRow;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisWorkflowRunRepositoryTest {

    @Test
    void saveRejectsNullTaskRun() {
        WorkflowRun workflowRun = workflowRun(
                "run-null-task",
                WorkflowId.of("workflow-null-task"),
                WorkflowRunStatus.PENDING,
                Instant.parse("2026-06-14T00:00:00Z"));
        MyBatisWorkflowRunRepository repository = new MyBatisWorkflowRunRepository(new FakeWorkflowRunMapper());

        assertThatThrownBy(() -> repository.save(workflowRun, Arrays.asList((TaskRun) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskRun must not be null");
    }

    @Test
    void saveRejectsTaskRunWithDifferentWorkflowRunId() {
        WorkflowRun workflowRun = workflowRun(
                "run-owner",
                WorkflowId.of("workflow-owner"),
                WorkflowRunStatus.PENDING,
                Instant.parse("2026-06-14T00:00:00Z"));
        TaskRun taskRun = taskRun(
                "task-run-owner",
                WorkflowRunId.of("different-run"),
                "task-owner",
                "Task Owner",
                TaskRunStatus.PENDING,
                0);
        MyBatisWorkflowRunRepository repository = new MyBatisWorkflowRunRepository(new FakeWorkflowRunMapper());

        assertThatThrownBy(() -> repository.save(workflowRun, List.of(taskRun)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task run workflowRunId must match workflow run id");
    }

    private static WorkflowRun workflowRun(
            String id,
            WorkflowId workflowId,
            WorkflowRunStatus status,
            Instant triggeredAt) {
        return WorkflowRun.restore(WorkflowRunId.of(id), workflowId, status, triggeredAt);
    }

    private static TaskRun taskRun(
            String id,
            WorkflowRunId workflowRunId,
            String taskId,
            String taskName,
            TaskRunStatus status,
            int position) {
        return TaskRun.restore(
                TaskRunId.of(id),
                workflowRunId,
                TaskId.of(taskId),
                taskName,
                status,
                position,
                Instant.parse("2026-06-14T00:00:00Z"));
    }

    private static final class FakeWorkflowRunMapper implements WorkflowRunMapper {

        @Override
        public void insertWorkflowRun(WorkflowRunRow workflowRun) {
            throw new AssertionError("workflow run should not be inserted when task run validation fails");
        }

        @Override
        public void insertTaskRuns(List<TaskRunRow> taskRuns) {
            throw new AssertionError("task runs should not be inserted when task run validation fails");
        }

        @Override
        public WorkflowRunRow findWorkflowRunById(String workflowRunId) {
            return null;
        }

        @Override
        public List<WorkflowRunRow> findWorkflowRunsByWorkflowId(String workflowId, int limit) {
            return List.of();
        }

        @Override
        public List<WorkflowRunRow> findWorkflowRunsByStatus(String status, int limit) {
            return List.of();
        }

        @Override
        public List<TaskRunRow> findTaskRunsByRunId(String workflowRunId) {
            return List.of();
        }

        @Override
        public List<TaskRunRow> findPendingTaskRunsByRunId(String workflowRunId) {
            return List.of();
        }

        @Override
        public void updateTaskRunStatus(String taskRunId, String status, String errorMessage,
                String output, Instant startedAt, Instant finishedAt, int retryCount) {
            // no-op for test
        }

        @Override
        public void updateWorkflowRunStatus(String workflowRunId, String status,
                Instant startedAt, Instant finishedAt) {
            // no-op for test
        }
    }
}
