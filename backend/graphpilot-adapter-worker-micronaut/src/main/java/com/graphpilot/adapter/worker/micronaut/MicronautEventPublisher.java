package com.graphpilot.adapter.worker.micronaut;

import com.graphpilot.application.execution.port.out.EventPublisherPort;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;

/**
 * Micronaut implementation of {@link EventPublisherPort}: publishes the
 * framework-free domain event wrapped in a Micronaut {@link ApplicationEvent}.
 */
@Singleton
public class MicronautEventPublisher implements EventPublisherPort {

    private final ApplicationEventPublisher<WorkflowRunCreatedApplicationEvent> eventPublisher;

    public MicronautEventPublisher(
            ApplicationEventPublisher<WorkflowRunCreatedApplicationEvent> eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(WorkflowRunCreatedEvent event) {
        eventPublisher.publishEvent(new WorkflowRunCreatedApplicationEvent(this, event));
    }
}
