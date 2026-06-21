package com.graphpilot.admin.application.workflow.port.out;

import com.graphpilot.domain.workflow.WorkflowId;

@FunctionalInterface
public interface IdGeneratorPort {

    WorkflowId nextWorkflowId();
}