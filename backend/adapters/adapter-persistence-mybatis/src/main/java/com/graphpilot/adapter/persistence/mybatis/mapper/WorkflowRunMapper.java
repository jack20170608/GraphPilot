package com.graphpilot.adapter.persistence.mybatis.mapper;

import com.graphpilot.adapter.persistence.mybatis.row.TaskRunRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRunRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowRunMapper {

    void insertWorkflowRun(WorkflowRunRow workflowRun);

    void insertTaskRuns(@Param("taskRuns") List<TaskRunRow> taskRuns);

    WorkflowRunRow findWorkflowRunById(@Param("workflowRunId") String workflowRunId);

    List<WorkflowRunRow> findWorkflowRunsByWorkflowId(
            @Param("workflowId") String workflowId,
            @Param("limit") int limit);

    List<WorkflowRunRow> findWorkflowRunsByStatus(
            @Param("status") String status,
            @Param("limit") int limit);

    List<TaskRunRow> findTaskRunsByRunId(@Param("workflowRunId") String workflowRunId);

    List<TaskRunRow> findPendingTaskRunsByRunId(@Param("workflowRunId") String workflowRunId);

    void updateTaskRunStatus(@Param("taskRunId") String taskRunId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("output") String output,
            @Param("startedAt") java.time.Instant startedAt,
            @Param("finishedAt") java.time.Instant finishedAt,
            @Param("retryCount") int retryCount);

    void updateWorkflowRunStatus(@Param("workflowRunId") String workflowRunId,
            @Param("status") String status,
            @Param("startedAt") java.time.Instant startedAt,
            @Param("finishedAt") java.time.Instant finishedAt);
}
