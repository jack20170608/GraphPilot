package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.TaskRunId;

@FunctionalInterface
public interface TaskRunIdGeneratorPort {

    TaskRunId nextTaskRunId();
}
