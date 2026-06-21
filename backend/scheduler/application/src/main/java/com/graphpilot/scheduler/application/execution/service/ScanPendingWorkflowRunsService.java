package com.graphpilot.scheduler.application.execution.service;

import com.graphpilot.scheduler.application.execution.ScanFailure;
import com.graphpilot.scheduler.application.execution.ScanResult;
import com.graphpilot.scheduler.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.scheduler.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScanPendingWorkflowRunsService implements ScanPendingWorkflowRunsUseCase {

    private final WorkflowRunRepository workflowRunRepository;
    private final ExecuteWorkflowRunUseCase executeWorkflowRunUseCase;

    public ScanPendingWorkflowRunsService(
            WorkflowRunRepository workflowRunRepository,
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        this.workflowRunRepository = Objects.requireNonNull(
                workflowRunRepository, "workflowRunRepository must not be null");
        this.executeWorkflowRunUseCase = Objects.requireNonNull(
                executeWorkflowRunUseCase, "executeWorkflowRunUseCase must not be null");
    }

    @Override
    public ScanResult scan(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Pending workflow run scan limit must be positive");
        }
        List<WorkflowRun> pendingRuns = workflowRunRepository.findByStatus(WorkflowRunStatus.PENDING, limit);
        List<ScanFailure> failures = new ArrayList<>();
        int executed = 0;
        for (WorkflowRun run : pendingRuns) {
            try {
                executeWorkflowRunUseCase.execute(run.id());
                executed++;
            } catch (RuntimeException e) {
                failures.add(new ScanFailure(run.id(), e.getMessage()));
            }
        }
        return new ScanResult(pendingRuns.size(), executed, failures.size(), failures);
    }
}