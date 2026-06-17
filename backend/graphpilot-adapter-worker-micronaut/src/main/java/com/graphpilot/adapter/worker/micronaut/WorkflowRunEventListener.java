package com.graphpilot.adapter.worker.micronaut;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for wrapped workflow-run-created events and drives execution via the
 * framework-free {@link ExecuteWorkflowRunUseCase}. Mirrors the Spring worker
 * adapter's listener but uses Micronaut's event API, reusing the same worker
 * core — proving the core is portable (ADR 0004).
 */
@Singleton
public class WorkflowRunEventListener implements ApplicationEventListener<WorkflowRunCreatedApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunEventListener.class);

    private final ExecuteWorkflowRunUseCase executeWorkflowRunUseCase;

    public WorkflowRunEventListener(ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        this.executeWorkflowRunUseCase = Objects.requireNonNull(
                executeWorkflowRunUseCase, "executeWorkflowRunUseCase must not be null");
    }

    @Override
    @Async
    public void onApplicationEvent(WorkflowRunCreatedApplicationEvent event) {
        var runId = event.domainEvent().workflowRunId();
        log.info("Received workflow run created event: {}", runId);
        try {
            executeWorkflowRunUseCase.execute(runId);
            log.info("Workflow run execution completed: {}", runId);
        } catch (Exception e) {
            log.error("Failed to execute workflow run: {}", runId, e);
        }
    }
}
