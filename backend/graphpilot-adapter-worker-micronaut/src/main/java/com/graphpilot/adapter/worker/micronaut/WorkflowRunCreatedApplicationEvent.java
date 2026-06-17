package com.graphpilot.adapter.worker.micronaut;

import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import io.micronaut.context.event.ApplicationEvent;
import java.util.Objects;

/**
 * Wraps the framework-free {@link WorkflowRunCreatedEvent} so it can travel over
 * Micronaut's event bus, which requires event payloads to extend
 * {@link ApplicationEvent}. The domain event stays framework-agnostic; this
 * wrapper exists only at the adapter boundary.
 */
public final class WorkflowRunCreatedApplicationEvent extends ApplicationEvent {

    private final WorkflowRunCreatedEvent domainEvent;

    public WorkflowRunCreatedApplicationEvent(Object source, WorkflowRunCreatedEvent domainEvent) {
        super(source);
        this.domainEvent = Objects.requireNonNull(domainEvent, "domainEvent must not be null");
    }

    public WorkflowRunCreatedEvent domainEvent() {
        return domainEvent;
    }
}
