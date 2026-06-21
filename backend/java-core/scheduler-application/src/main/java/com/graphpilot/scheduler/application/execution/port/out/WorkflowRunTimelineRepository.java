package com.graphpilot.scheduler.application.execution.port.out;

import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.List;

public interface WorkflowRunTimelineRepository {
    WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event);

    List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit);
}