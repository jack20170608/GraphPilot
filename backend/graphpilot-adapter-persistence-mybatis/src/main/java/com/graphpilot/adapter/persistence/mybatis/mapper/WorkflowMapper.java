package com.graphpilot.adapter.persistence.mybatis.mapper;

import com.graphpilot.adapter.persistence.mybatis.row.WorkflowEdgeRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowTaskRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowMapper {

    void upsertWorkflow(WorkflowRow workflow);

    void deleteTasksByWorkflowId(@Param("workflowId") String workflowId);

    void deleteEdgesByWorkflowId(@Param("workflowId") String workflowId);

    void insertTasks(@Param("tasks") List<WorkflowTaskRow> tasks);

    void insertEdges(@Param("edges") List<WorkflowEdgeRow> edges);

    WorkflowRow findWorkflowById(@Param("workflowId") String workflowId);

    List<WorkflowTaskRow> findTasksByWorkflowId(@Param("workflowId") String workflowId);

    List<WorkflowTaskRow> findTasksByWorkflowIds(@Param("workflowIds") List<String> workflowIds);

    List<WorkflowEdgeRow> findEdgesByWorkflowId(@Param("workflowId") String workflowId);

    List<WorkflowEdgeRow> findEdgesByWorkflowIds(@Param("workflowIds") List<String> workflowIds);

    List<WorkflowRow> findAllWorkflows(@Param("limit") int limit);
}
