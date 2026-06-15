package com.graphpilot.adapter.worker.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class WorkflowRunEventListenerTest {

    private ExecuteWorkflowRunUseCase mockUseCase;
    private Executor executor;

    @BeforeEach
    void setUp() {
        mockUseCase = mock(ExecuteWorkflowRunUseCase.class);
        executor = Executors.newSingleThreadExecutor();
    }

    @Test
    @Timeout(5)
    void onWorkflowRunCreatedTriggersExecution() throws InterruptedException {
        WorkflowRunEventListener listener = new WorkflowRunEventListener(mockUseCase, executor);
        WorkflowRunId runId = WorkflowRunId.of("run-123");
        WorkflowId workflowId = WorkflowId.of("workflow-1");
        WorkflowRunCreatedEvent event = new WorkflowRunCreatedEvent(runId, workflowId);

        listener.onWorkflowRunCreated(event);

        // Wait for async execution
        Thread.sleep(500);

        verify(mockUseCase, times(1)).execute(runId);
    }

    @Test
    @Timeout(5)
    void onWorkflowRunCreatedHandlesException() throws InterruptedException {
        doThrow(new RuntimeException("Execution failed"))
                .when(mockUseCase).execute(any());

        WorkflowRunEventListener listener = new WorkflowRunEventListener(mockUseCase, executor);
        WorkflowRunCreatedEvent event = new WorkflowRunCreatedEvent(
                WorkflowRunId.of("run-1"),
                WorkflowId.of("workflow-1"));

        // Should not throw
        listener.onWorkflowRunCreated(event);

        // Wait for async execution
        Thread.sleep(500);

        // Verify it was called despite exception
        verify(mockUseCase, times(1)).execute(any());
    }
}