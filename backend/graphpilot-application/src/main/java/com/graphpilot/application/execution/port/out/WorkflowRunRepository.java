package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.List;
import java.util.Optional;

public interface WorkflowRunRepository {

    WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns);

    Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId);

    List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit);

    List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);
}
