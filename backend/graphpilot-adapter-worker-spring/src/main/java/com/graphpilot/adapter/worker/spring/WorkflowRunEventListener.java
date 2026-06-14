package com.graphpilot.adapter.worker.spring;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for workflow run created events and triggers execution.
 */
@Component
public class WorkflowRunEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunEventListener.class);

    private final ExecuteWorkflowRunUseCase executeWorkflowRunUseCase;
    private final Executor executor;

    public WorkflowRunEventListener(
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase,
            Executor executor) {
        this.executeWorkflowRunUseCase = executeWorkflowRunUseCase;
        this.executor = executor;
    }

    @EventListener
    @Async
    public void onWorkflowRunCreated(WorkflowRunCreatedEvent event) {
        log.info("Received workflow run created event: {}", event.workflowRunId());

        executor.execute(() -> {
            try {
                executeWorkflowRunUseCase.execute(event.workflowRunId());
                log.info("Workflow run execution completed: {}", event.workflowRunId());
            } catch (Exception e) {
                log.error("Failed to execute workflow run: {}", event.workflowRunId(), e);
            }
        });
    }
}