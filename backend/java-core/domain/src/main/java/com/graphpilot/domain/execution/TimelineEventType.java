package com.graphpilot.domain.execution;

public enum TimelineEventType {
    RUN_CREATED,
    RUN_STARTED,
    TASK_STARTED,
    TASK_SUCCEEDED,
    TASK_FAILED,
    TASK_SKIPPED,
    RUN_SUCCEEDED,
    RUN_FAILED
}
