package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.WorkflowRunId;

@FunctionalInterface
public interface WorkflowRunIdGeneratorPort {

    WorkflowRunId nextWorkflowRunId();
}
