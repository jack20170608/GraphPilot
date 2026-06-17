package com.graphpilot.application.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.ScanFailure;
import com.graphpilot.application.execution.ScanResult;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScanPendingWorkflowRunsServiceTest {

    @Test
    void rejectsNonPositiveLimit() {
        var service = new ScanPendingWorkflowRunsService(new FakeRunRepository(), id -> { });

        assertThatThrownBy(() -> service.scan(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pending workflow run scan limit must be positive");
    }

    @Test
    void scansPendingRunsAndExecutesEach() {
        FakeRunRepository repository = new FakeRunRepository();
        WorkflowRun run1 = run("run-1", WorkflowRunStatus.PENDING);
        WorkflowRun run2 = run("run-2", WorkflowRunStatus.PENDING);
        repository.runs.add(run1);
        repository.runs.add(run2);
        RecordingExecutor executor = new RecordingExecutor();
        var service = new ScanPendingWorkflowRunsService(repository, executor);

        ScanResult result = service.scan(10);

        assertThat(repository.lastStatus).isEqualTo(WorkflowRunStatus.PENDING);
        assertThat(repository.lastLimit).isEqualTo(10);
        assertThat(executor.executed).containsExactly(run1.id(), run2.id());
        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.executedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void isolatesExecutionFailures() {
        FakeRunRepository repository = new FakeRunRepository();
        WorkflowRun run1 = run("run-1", WorkflowRunStatus.PENDING);
        WorkflowRun run2 = run("run-2", WorkflowRunStatus.PENDING);
        repository.runs.add(run1);
        repository.runs.add(run2);
        RecordingExecutor executor = new RecordingExecutor();
        executor.failures.add(run1.id());
        var service = new ScanPendingWorkflowRunsService(repository, executor);

        ScanResult result = service.scan(10);

        assertThat(executor.executed).containsExactly(run1.id(), run2.id());
        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.executedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(ScanFailure::workflowRunId)
                .containsExactly(run1.id());
    }

    private static WorkflowRun run(String id, WorkflowRunStatus status) {
        return WorkflowRun.restore(
                WorkflowRunId.of(id),
                WorkflowId.of("workflow-1"),
                status,
                Instant.parse("2026-06-17T00:00:00Z"));
    }

    private static final class RecordingExecutor implements ExecuteWorkflowRunUseCase {
        final List<WorkflowRunId> executed = new ArrayList<>();
        final List<WorkflowRunId> failures = new ArrayList<>();

        @Override
        public void execute(WorkflowRunId workflowRunId) {
            executed.add(workflowRunId);
            if (failures.contains(workflowRunId)) {
                throw new IllegalStateException("boom " + workflowRunId.value());
            }
        }
    }

    private static final class FakeRunRepository implements WorkflowRunRepository {
        final List<WorkflowRun> runs = new ArrayList<>();
        WorkflowRunStatus lastStatus;
        int lastLimit;

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
            return Optional.empty();
        }

        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
            return List.of();
        }

        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
            return List.of();
        }

        @Override
        public List<WorkflowRun> findByStatus(WorkflowRunStatus status, int limit) {
            lastStatus = status;
            lastLimit = limit;
            return runs.stream().limit(limit).toList();
        }
    }
}
