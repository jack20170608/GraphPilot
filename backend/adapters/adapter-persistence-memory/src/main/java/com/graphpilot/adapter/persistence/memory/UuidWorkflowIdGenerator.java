package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.shared.port.IdGeneratorPort;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.UUID;

public final class UuidWorkflowIdGenerator implements IdGeneratorPort {

    @Override
    public WorkflowId nextWorkflowId() {
        return WorkflowId.of(UUID.randomUUID().toString());
    }
}
