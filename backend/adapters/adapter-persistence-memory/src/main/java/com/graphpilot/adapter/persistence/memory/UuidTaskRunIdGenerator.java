package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.scheduler.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.domain.execution.TaskRunId;
import java.util.UUID;

public final class UuidTaskRunIdGenerator implements TaskRunIdGeneratorPort {

    @Override
    public TaskRunId nextTaskRunId() {
        return TaskRunId.of(UUID.randomUUID().toString());
    }
}
