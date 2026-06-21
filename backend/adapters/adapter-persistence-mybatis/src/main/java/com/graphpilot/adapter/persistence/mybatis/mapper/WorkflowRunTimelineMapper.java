package com.graphpilot.adapter.persistence.mybatis.mapper;

import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRunTimelineEventRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowRunTimelineMapper {

    void insert(WorkflowRunTimelineEventRow row);

    List<WorkflowRunTimelineEventRow> findByWorkflowRunId(
            @Param("workflowRunId") String workflowRunId,
            @Param("limit") int limit);
}
