package com.graphpilot.adapter.worker.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import io.micronaut.context.event.ApplicationEventPublisher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Micronaut glue wires the framework-free worker core end to end
 * without a full Micronaut runtime: the publisher emits a wrapped event, the
 * listener consumes it and invokes the use case. This proves the worker core
 * (ADR 0004) is reusable from a Micronaut host.
 */
class WorkflowRunEventListenerTest {

    @Test
    void publisherAndListenerRouteDomainEventToUseCase() {
        RecordingExecuteUseCase useCase = new RecordingExecuteUseCase();
        RecordingEventPublisher<WorkflowRunCreatedApplicationEvent> bus = new RecordingEventPublisher<>();
        MicronautEventPublisher publisher = new MicronautEventPublisher(bus);
        WorkflowRunEventListener listener = new WorkflowRunEventListener(useCase);

        WorkflowRunCreatedEvent domainEvent = new WorkflowRunCreatedEvent(
                WorkflowRunId.of("run-1"),
                WorkflowId.of("workflow-1"));
        publisher.publish(domainEvent);

        // The publisher must have wrapped and emitted the event onto the bus.
        assertThat(bus.published).hasSize(1);
        assertThat(bus.published.get(0).domainEvent()).isEqualTo(domainEvent);

        // The listener consumes the wrapped event and drives the use case.
        listener.onApplicationEvent(bus.published.get(0));
        assertThat(useCase.executed).containsExactly(WorkflowRunId.of("run-1"));
    }

    private static final class RecordingExecuteUseCase implements ExecuteWorkflowRunUseCase {
        final List<WorkflowRunId> executed = new ArrayList<>();

        @Override
        public void execute(WorkflowRunId workflowRunId) {
            executed.add(workflowRunId);
        }
    }

    private static final class RecordingEventPublisher<E>
            implements ApplicationEventPublisher<E> {
        final List<E> published = new ArrayList<>();

        @Override
        public void publishEvent(E event) {
            published.add(event);
        }
    }
}
