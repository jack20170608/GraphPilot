package com.graphpilot.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.port.out.EventPublisherPort;
import com.graphpilot.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.service.TriggerWorkflowRunService;
import com.graphpilot.application.workflow.WorkflowNotFoundException;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.execution.WorkflowRunTriggerException;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TriggerWorkflowRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");
    private static final WorkflowRunId WORKFLOW_RUN_ID = WorkflowRunId.of("run-1");

    private RecordingWorkflowRepository workflowRepository;
    private RecordingWorkflowRunRepository workflowRunRepository;
    private QueueWorkflowRunIdGenerator workflowRunIdGenerator;
    private QueueTaskRunIdGenerator taskRunIdGenerator;
    private FixedClock clock;
    private NoOpEventPublisher eventPublisher;
    private TriggerWorkflowRunService service;

    @BeforeEach
    void setUp() {
        workflowRepository = new RecordingWorkflowRepository();
        workflowRunRepository = new RecordingWorkflowRunRepository();
        workflowRunIdGenerator = new QueueWorkflowRunIdGenerator(WORKFLOW_RUN_ID);
        taskRunIdGenerator = new QueueTaskRunIdGenerator(
                TaskRunId.of("task-run-1"),
                TaskRunId.of("task-run-2"),
                TaskRunId.of("task-run-3"));
        clock = new FixedClock(NOW);
        eventPublisher = new NoOpEventPublisher();
        service = new TriggerWorkflowRunService(
                workflowRepository,
                workflowRunRepository,
                workflowRunIdGenerator,
                taskRunIdGenerator,
                clock,
                eventPublisher);
    }

    @Test
    void activeWorkflowCreatesOneRunAndTaskRunsForEachWorkflowTask() {
        workflowRepository.workflow = workflow(WorkflowStatus.ACTIVE,
                List.of(task("extract", "Extract data"), task("load", "Load data")));

        WorkflowRunId triggeredRunId = service.trigger(WORKFLOW_ID);

        assertThat(triggeredRunId).isEqualTo(WORKFLOW_RUN_ID);
        assertThat(workflowRunRepository.savedRun)
                .isEqualTo(WorkflowRun.restore(
                        WORKFLOW_RUN_ID,
                        WORKFLOW_ID,
                        WorkflowRunStatus.PENDING,
                        NOW));
        assertThat(workflowRunRepository.savedTaskRuns)
                .hasSize(2)
                .extracting(TaskRun::id)
                .containsExactly(TaskRunId.of("task-run-1"), TaskRunId.of("task-run-2"));
        assertThat(workflowRunRepository.savedTaskRuns)
                .extracting(TaskRun::workflowRunId)
                .containsExactly(WORKFLOW_RUN_ID, WORKFLOW_RUN_ID);
        assertThat(workflowRunRepository.savedTaskRuns)
                .extracting(TaskRun::status)
                .containsExactly(TaskRunStatus.PENDING, TaskRunStatus.PENDING);
        assertThat(workflowRunRepository.savedTaskRuns)
                .extracting(TaskRun::createdAt)
                .containsExactly(NOW, NOW);
        assertThat(clock.callCount).isEqualTo(1);
    }

    @Test
    void taskRunOrderFollowsWorkflowDagTasksOrder() {
        workflowRepository.workflow = workflow(WorkflowStatus.ACTIVE,
                List.of(
                        task("extract", "Extract data"),
                        task("transform", "Transform data"),
                        task("load", "Load data")));

        service.trigger(WORKFLOW_ID);

        assertThat(workflowRunRepository.savedTaskRuns)
                .extracting(TaskRun::taskId)
                .containsExactly(TaskId.of("extract"), TaskId.of("transform"), TaskId.of("load"));
        assertThat(workflowRunRepository.savedTaskRuns)
                .extracting(TaskRun::position)
                .containsExactly(0, 1, 2);
    }

    @Test
    void missingWorkflowThrowsWorkflowNotFoundException() {
        workflowRepository.workflow = null;

        assertThatThrownBy(() -> service.trigger(WORKFLOW_ID))
                .isInstanceOf(WorkflowNotFoundException.class)
                .hasMessage("Workflow not found: id=workflow-1");
        assertThat(workflowRunRepository.saveCount).isZero();
    }

    @Test
    void nonActiveWorkflowThrowsWorkflowRunTriggerExceptionAndDoesNotSave() {
        workflowRepository.workflow = workflow(WorkflowStatus.DRAFT, List.of(task("extract", "Extract data")));

        assertThatThrownBy(() -> service.trigger(WORKFLOW_ID))
                .isInstanceOf(WorkflowRunTriggerException.class)
                .hasMessage("Workflow run can only be triggered for ACTIVE workflow");
        assertThat(workflowRunRepository.saveCount).isZero();
    }

    @Test
    void nullWorkflowIdThrowsNullPointerException() {
        assertThatThrownBy(() -> service.trigger(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workflowId must not be null");
        assertThat(workflowRunRepository.saveCount).isZero();
    }

    private static TaskDefinition task(String id, String name) {
        return new TaskDefinition(TaskId.of(id), name);
    }

    private static Workflow workflow(WorkflowStatus status, List<TaskDefinition> tasks) {
        return Workflow.restore(
                WORKFLOW_ID,
                WorkflowName.of("Daily ETL"),
                new DagDefinition(tasks, List.of()),
                status,
                Instant.parse("2026-06-13T00:00:00Z"));
    }

    private static final class RecordingWorkflowRepository implements WorkflowRepository {

        private Workflow workflow;

        @Override
        public Workflow save(Workflow workflow) {
            this.workflow = workflow;
            return workflow;
        }

        @Override
        public Optional<Workflow> findById(WorkflowId workflowId) {
            return Optional.ofNullable(workflow);
        }

        @Override
        public List<Workflow> findAll(int limit) {
            return workflow == null ? List.of() : List.of(workflow);
        }
    }

    private static final class RecordingWorkflowRunRepository implements WorkflowRunRepository {

        private WorkflowRun savedRun;
        private List<TaskRun> savedTaskRuns = List.of();
        private int saveCount;

        @Override
        public WorkflowRun save(WorkflowRun workflowRun, List<TaskRun> taskRuns) {
            savedRun = workflowRun;
            savedTaskRuns = List.copyOf(taskRuns);
            saveCount++;
            return workflowRun;
        }

        @Override
        public Optional<WorkflowRun> findRunById(WorkflowRunId workflowRunId) {
            return Optional.ofNullable(savedRun);
        }

        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
            return savedRun == null ? List.of() : List.of(savedRun);
        }

        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
            return savedTaskRuns;
        }
    }

    private static final class QueueWorkflowRunIdGenerator implements WorkflowRunIdGeneratorPort {

        private final ArrayDeque<WorkflowRunId> ids;

        private QueueWorkflowRunIdGenerator(WorkflowRunId... ids) {
            this.ids = new ArrayDeque<>(List.of(ids));
        }

        @Override
        public WorkflowRunId nextWorkflowRunId() {
            return ids.removeFirst();
        }
    }

    private static final class QueueTaskRunIdGenerator implements TaskRunIdGeneratorPort {

        private final ArrayDeque<TaskRunId> ids;

        private QueueTaskRunIdGenerator(TaskRunId... ids) {
            this.ids = new ArrayDeque<>(List.of(ids));
        }

        @Override
        public TaskRunId nextTaskRunId() {
            return ids.removeFirst();
        }
    }

    private static final class FixedClock implements ClockPort {

        private final Instant now;
        private int callCount;

        private FixedClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            callCount++;
            return now;
        }
    }

    private static final class NoOpEventPublisher implements EventPublisherPort {

        @Override
        public void publish(com.graphpilot.domain.execution.WorkflowRunCreatedEvent event) {
            // no-op for testing
        }
    }
}