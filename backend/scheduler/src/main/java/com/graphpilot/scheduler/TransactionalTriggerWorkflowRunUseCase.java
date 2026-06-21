package com.graphpilot.scheduler;

import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务性触发工作流执行。
 * 确保 workflow run 创建和初始状态在同一个事务中。
 */
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