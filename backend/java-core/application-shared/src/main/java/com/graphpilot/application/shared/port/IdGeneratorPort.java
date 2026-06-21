package com.graphpilot.application.shared.port;

import com.graphpilot.domain.workflow.WorkflowId;

/**
 * Port for generating workflow IDs.
 */
@FunctionalInterface
public interface IdGeneratorPort {

    WorkflowId nextWorkflowId();
}