package com.graphpilot.scheduler.application.execution.port.out;

import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;

/**
 * Port for publishing domain events related to workflow execution.
 */
public interface EventPublisherPort {

    /**
     * Publish a workflow run created event to trigger worker execution.
     */
    void publish(WorkflowRunCreatedEvent event);
}