package com.graphpilot.bootstrap.spring;

import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalTriggerWorkflowRunUseCase implements TriggerWorkflowRunUseCase {

    private static final int TRIGGER_TRANSACTION_TIMEOUT_SECONDS = 5;

    private final TriggerWorkflowRunUseCase delegate;

    public TransactionalTriggerWorkflowRunUseCase(TriggerWorkflowRunUseCase delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    @Transactional(timeout = TRIGGER_TRANSACTION_TIMEOUT_SECONDS)
    public WorkflowRunId trigger(WorkflowId workflowId) {
        return delegate.trigger(workflowId);
    }
}
