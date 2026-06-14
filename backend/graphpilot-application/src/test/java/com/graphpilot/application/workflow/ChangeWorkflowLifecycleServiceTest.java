package com.graphpilot.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.ChangeWorkflowLifecycleService;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeWorkflowLifecycleServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-13T00:00:00Z");
    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");

    private RecordingWorkflowRepository workflowRepository;
    private ChangeWorkflowLifecycleService service;

    @BeforeEach
    void setUp() {
        workflowRepository = new RecordingWorkflowRepository();
        service = new ChangeWorkflowLifecycleService(workflowRepository);
    }

    @Test
    void activateTransitionsDraftWorkflowAndSavesIt() {
        workflowRepository.storedWorkflow = workflow(WorkflowStatus.DRAFT);

        Workflow savedWorkflow = service.activate(WORKFLOW_ID);

        assertThat(savedWorkflow.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(workflowRepository.requestedWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRepository.savedWorkflow).isEqualTo(savedWorkflow);
    }

    @Test
    void pauseTransitionsActiveWorkflowAndSavesIt() {
        workflowRepository.storedWorkflow = workflow(WorkflowStatus.ACTIVE);

        Workflow savedWorkflow = service.pause(WORKFLOW_ID);

        assertThat(savedWorkflow.status()).isEqualTo(WorkflowStatus.PAUSED);
        assertThat(workflowRepository.requestedWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRepository.savedWorkflow).isEqualTo(savedWorkflow);
    }

    @Test
    void resumeTransitionsPausedWorkflowAndSavesIt() {
        workflowRepository.storedWorkflow = workflow(WorkflowStatus.PAUSED);

        Workflow savedWorkflow = service.resume(WORKFLOW_ID);

        assertThat(savedWorkflow.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(workflowRepository.requestedWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRepository.savedWorkflow).isEqualTo(savedWorkflow);
    }

    @Test
    void archiveTransitionsActiveWorkflowAndSavesIt() {
        workflowRepository.storedWorkflow = workflow(WorkflowStatus.ACTIVE);

        Workflow savedWorkflow = service.archive(WORKFLOW_ID);

        assertThat(savedWorkflow.status()).isEqualTo(WorkflowStatus.ARCHIVED);
        assertThat(workflowRepository.requestedWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRepository.savedWorkflow).isEqualTo(savedWorkflow);
    }

    @Test
    void missingWorkflowThrowsWorkflowNotFoundExceptionAndDoesNotSave() {
        workflowRepository.storedWorkflow = null;

        assertThatThrownBy(() -> service.activate(WORKFLOW_ID))
                .isInstanceOf(WorkflowNotFoundException.class)
                .hasMessage("Workflow not found: id=workflow-1");
        assertThat(workflowRepository.requestedWorkflowId).isEqualTo(WORKFLOW_ID);
        assertThat(workflowRepository.savedWorkflow).isNull();
    }

    @Test
    void nullWorkflowIdThrowsNullPointerException() {
        assertThatThrownBy(() -> service.activate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workflowId must not be null");
    }

    private static Workflow workflow(WorkflowStatus status) {
        DagDefinition dag = new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());
        return Workflow.restore(
                WORKFLOW_ID,
                WorkflowName.of("Daily ETL"),
                dag,
                status,
                CREATED_AT);
    }

    private static final class RecordingWorkflowRepository implements WorkflowRepository {

        private Workflow storedWorkflow;
        private WorkflowId requestedWorkflowId;
        private Workflow savedWorkflow;

        @Override
        public Workflow save(Workflow workflow) {
            savedWorkflow = workflow;
            return workflow;
        }

        @Override
        public Optional<Workflow> findById(WorkflowId workflowId) {
            requestedWorkflowId = workflowId;
            return Optional.ofNullable(storedWorkflow);
        }

        @Override
        public List<Workflow> findAll(int limit) {
            return List.of();
        }
    }
}
