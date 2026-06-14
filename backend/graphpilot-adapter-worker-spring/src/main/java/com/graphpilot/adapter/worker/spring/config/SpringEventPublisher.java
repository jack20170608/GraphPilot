package com.graphpilot.adapter.worker.spring.config;

import com.graphpilot.application.execution.port.out.EventPublisherPort;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring implementation of EventPublisherPort.
 */
@Component
public class SpringEventPublisher implements EventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;

    public SpringEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(WorkflowRunCreatedEvent event) {
        eventPublisher.publishEvent(event);
    }
}