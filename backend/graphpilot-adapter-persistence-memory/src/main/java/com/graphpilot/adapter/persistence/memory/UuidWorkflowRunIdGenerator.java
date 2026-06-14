package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.domain.execution.WorkflowRunId;
import java.util.UUID;

public final class UuidWorkflowRunIdGenerator implements WorkflowRunIdGeneratorPort {

    @Override
    public WorkflowRunId nextWorkflowRunId() {
        return WorkflowRunId.of(UUID.randomUUID().toString());
    }
}
