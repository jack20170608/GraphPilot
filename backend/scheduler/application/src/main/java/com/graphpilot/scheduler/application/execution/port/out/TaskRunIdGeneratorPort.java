package com.graphpilot.scheduler.application.execution.port.out;

import com.graphpilot.domain.execution.TaskRunId;

@FunctionalInterface
public interface TaskRunIdGeneratorPort {

    TaskRunId nextTaskRunId();
}