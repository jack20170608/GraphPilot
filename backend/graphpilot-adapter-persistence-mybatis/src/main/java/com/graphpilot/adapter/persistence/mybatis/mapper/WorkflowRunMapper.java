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

    List<TaskRunRow> findTaskRunsByRunId(@Param("workflowRunId") String workflowRunId);
}
