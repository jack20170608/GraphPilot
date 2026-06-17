package com.graphpilot.application.execution.port.in;

import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.List;

public interface QueryWorkflowRunUseCase {

    WorkflowRun findRunById(WorkflowRunId workflowRunId);

    List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit);

    List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);

    List<WorkflowRunTimelineEvent> findTimelineByRunId(WorkflowRunId workflowRunId, int limit);
}
