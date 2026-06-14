package com.graphpilot.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.service.QueryWorkflowRunService;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryWorkflowRunServiceTest {

    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");
    private static final WorkflowRunId RUN_ID = WorkflowRunId.of("run-1");
    private static final Instant TRIGGERED_AT = Instant.parse("2026-06-14T00:00:00Z");

    private RecordingWorkflowRunRepository workflowRunRepository;
    private QueryWorkflowRunService service;

    @BeforeEach
    void setUp() {
        workflowRunRepository = new RecordingWorkflowRunRepository();
        service = new QueryWorkflowRunService(workflowRunRepository);
    }

    @Test
    void findRunByIdReturnsExistingRun() {
        WorkflowRun workflowRun = workflowRun(RUN_ID, WORKFLOW_ID);
        workflowRunRepository.runs = List.of(workflowRun);

        WorkflowRun foundRun = service.findRunById(RUN_ID);

        assertThat(foundRun).isEqualTo(workflowRun);
    }

    @Test
    void findRunByIdMissingThrowsWorkflowRunNotFoundException() {
        workflowRunRepository.runs = List.of();

        assertThatThrownBy(() -> service.findRunById(RUN_ID))
                .isInstanceOf(WorkflowRunNotFoundException.class)
                .hasMessage("Workflow run not found: id=run-1");
    }

    @Test
    void findRunsByWorkflowIdRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> service.findRunsByWorkflowId(WORKFLOW_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow run query limit must be positive");
    }

    @Test
    void findRunsByWorkflowIdReturnsRepositoryRuns() {
        WorkflowRun firstRun = workflowRun(WorkflowRunId.of("run-1"), WORKFLOW_ID);
        WorkflowRun secondRun = workflowRun(WorkflowRunId.of("run-2"), WORKFLOW_ID);
        workflowRunRepository.runs = List.of(firstRun, secondRun);

        List<WorkflowRun> workflowRuns = service.findRunsByWorkflowId(WORKFLOW_ID, 50);

        assertThat(workflowRuns).containsExactly(firstRun, secondRun);
        assertThatThrownBy(() -> workflowRuns.add(workflowRun(WorkflowRunId.of("run-3"), WORKFLOW_ID)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(workflowRunRepository.lastWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRunRepository.lastLimit).isEqualTo(50);
    }

    @Test
    void findTaskRunsByRunIdConfirmsRunExistsBeforeReturningTaskRuns() {
        WorkflowRun workflowRun = workflowRun(RUN_ID, WORKFLOW_ID);
        TaskRun taskRun = TaskRun.restore(
                TaskRunId.of("task-run-1"),
                RUN_ID,
                TaskId.of("extract"),
                "Extract data",
                TaskRunStatus.PENDING,
                0,
                TRIGGERED_AT);
        workflowRunRepository.runs = List.of(workflowRun);
        workflowRunRepository.taskRuns = List.of(taskRun);

        List<TaskRun> taskRuns = service.findTaskRunsByRunId(RUN_ID);

        assertThat(taskRuns).containsExactly(taskRun);
        assertThatThrownBy(() -> taskRuns.add(taskRun))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(workflowRunRepository.calls).containsExactly("findRunById", "findTaskRunsByRunId");
    }

    @Test
    void findTaskRunsByRunIdMissingRunThrowsBeforeLoadingTaskRuns() {
        workflowRunRepository.runs = List.of();

        assertThatThrownBy(() -> service.findTaskRunsByRunId(RUN_ID))
                .isInstanceOf(WorkflowRunNotFoundException.class)
                .hasMessage("Workflow run not found: id=run-1");
        assertThat(workflowRunRepository.calls).containsExactly("findRunById");
    }

    private static WorkflowRun workflowRun(WorkflowRunId runId, WorkflowId workflowId) {
        return WorkflowRun.restore(runId, workflowId, WorkflowRunStatus.PENDING, TRIGGERED_AT);
    }

    private static final class RecordingWorkflowRunRepository implements WorkflowRunRepository {

        private List<WorkflowRun> runs = List.of();
        private List<TaskRun> taskRuns = List.of();
        private final List<String> calls = new java.util.ArrayList<>();
        private WorkflowId lastWorkflowId;
        private int lastLimit;

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
            runs = List.of(workflowRun);
            this.taskRuns = List.copyOf(taskRuns);
            return workflowRun;
        }

        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
            calls.add("findRunById");
            return runs.stream()
                    .filter(workflowRun -> workflowRun.id().equals(workflowRunId))
                    .findFirst();
        }

        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
            lastWorkflowId = workflowId;
            lastLimit = limit;
            return new ArrayList<>(runs.stream()
                    .filter(workflowRun -> workflowRun.workflowId().equals(workflowId))
                    .limit(limit)
                    .toList());
        }

        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
            calls.add("findTaskRunsByRunId");
            return new ArrayList<>(taskRuns.stream()
                    .filter(taskRun -> taskRun.workflowRunId().equals(workflowRunId))
                    .toList());
        }
    }
}
