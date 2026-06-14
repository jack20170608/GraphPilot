package com.graphpilot.domain.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class WorkflowTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void createsWorkflowWithValidValues() {
        DagDefinition dag = dag();

        Workflow workflow = Workflow.create(
                WorkflowId.of("workflow-1"),
                WorkflowName.of("Daily ETL"),
                dag,
                CREATED_AT);

        assertEquals(WorkflowId.of("workflow-1"), workflow.id());
        assertEquals("Daily ETL", workflow.name().value());
        assertEquals(dag, workflow.dag());
        assertEquals(CREATED_AT, workflow.createdAt());
    }

    @Test
    void trimsWorkflowIdAndName() {
        assertEquals(WorkflowId.of("workflow-1"), WorkflowId.of(" workflow-1 "));
        assertEquals("Daily ETL", WorkflowName.of(" Daily ETL ").value());
    }

    @Test
    void rejectsBlankWorkflowId() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowId.of(" "));
    }

    @Test
    void rejectsBlankWorkflowName() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowName.of(" "));
    }

    @Test
    void rejectsMissingRequiredFields() {
        DagDefinition dag = dag();

        assertThrows(
                NullPointerException.class,
                () -> Workflow.create(null, WorkflowName.of("Daily ETL"), dag, CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> Workflow.create(WorkflowId.of("workflow-1"), null, dag, CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> Workflow.create(
                        WorkflowId.of("workflow-1"),
                        WorkflowName.of("Daily ETL"),
                        null,
                        CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> Workflow.create(
                        WorkflowId.of("workflow-1"),
                        WorkflowName.of("Daily ETL"),
                        dag,
                        null));
    }

    @Test
    void createsWorkflowInDraftStatusByDefault() {
        Workflow workflow = workflow();

        assertEquals(WorkflowStatus.DRAFT, workflow.status());
    }

    @Test
    void transitionsFromDraftToActive() {
        Workflow workflow = workflow();

        Workflow activatedWorkflow = workflow.activate();

        assertEquals(WorkflowStatus.ACTIVE, activatedWorkflow.status());
        assertEquals(WorkflowStatus.DRAFT, workflow.status());
    }

    @Test
    void transitionsFromActiveToPausedAndBackToActive() {
        Workflow activeWorkflow = workflow().activate();

        Workflow pausedWorkflow = activeWorkflow.pause();
        Workflow resumedWorkflow = pausedWorkflow.resume();

        assertEquals(WorkflowStatus.PAUSED, pausedWorkflow.status());
        assertEquals(WorkflowStatus.ACTIVE, resumedWorkflow.status());
        assertEquals(WorkflowStatus.ACTIVE, activeWorkflow.status());
    }

    @Test
    void archivesActiveAndPausedWorkflows() {
        Workflow archivedFromActive = workflow().activate().archive();
        Workflow archivedFromPaused = workflow().activate().pause().archive();

        assertEquals(WorkflowStatus.ARCHIVED, archivedFromActive.status());
        assertEquals(WorkflowStatus.ARCHIVED, archivedFromPaused.status());
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        Workflow draftWorkflow = workflow();
        Workflow activeWorkflow = draftWorkflow.activate();
        Workflow pausedWorkflow = activeWorkflow.pause();
        Workflow archivedWorkflow = activeWorkflow.archive();

        assertInvalidTransition(
                draftWorkflow::pause,
                "Cannot pause workflow from status DRAFT");
        assertInvalidTransition(
                draftWorkflow::resume,
                "Cannot resume workflow from status DRAFT");
        assertInvalidTransition(
                draftWorkflow::archive,
                "Cannot archive workflow from status DRAFT");
        assertInvalidTransition(
                activeWorkflow::activate,
                "Cannot activate workflow from status ACTIVE");
        assertInvalidTransition(
                activeWorkflow::resume,
                "Cannot resume workflow from status ACTIVE");
        assertInvalidTransition(
                pausedWorkflow::pause,
                "Cannot pause workflow from status PAUSED");
        assertInvalidTransition(
                pausedWorkflow::activate,
                "Cannot activate workflow from status PAUSED");
        assertInvalidTransition(
                archivedWorkflow::activate,
                "Cannot activate workflow from status ARCHIVED");
        assertInvalidTransition(
                archivedWorkflow::pause,
                "Cannot pause workflow from status ARCHIVED");
        assertInvalidTransition(
                archivedWorkflow::resume,
                "Cannot resume workflow from status ARCHIVED");
        assertInvalidTransition(
                archivedWorkflow::archive,
                "Cannot archive workflow from status ARCHIVED");
    }

    @Test
    void restoresWorkflowWithPersistedStatus() {
        DagDefinition dag = dag();

        Workflow workflow = Workflow.restore(
                WorkflowId.of("workflow-1"),
                WorkflowName.of("Daily ETL"),
                dag,
                WorkflowStatus.PAUSED,
                CREATED_AT);

        assertEquals(WorkflowStatus.PAUSED, workflow.status());
        assertEquals(dag, workflow.dag());
        assertEquals(CREATED_AT, workflow.createdAt());
    }

    @Test
    void rejectsMissingWorkflowStatus() {
        assertThrows(
                NullPointerException.class,
                () -> Workflow.restore(
                        WorkflowId.of("workflow-1"),
                        WorkflowName.of("Daily ETL"),
                        dag(),
                        null,
                        CREATED_AT));
    }

    private static void assertInvalidTransition(Executable transition, String expectedMessage) {
        WorkflowLifecycleException exception = assertThrows(
                WorkflowLifecycleException.class,
                transition);

        assertEquals(expectedMessage, exception.getMessage());
    }

    private static Workflow workflow() {
        return Workflow.create(
                WorkflowId.of("workflow-1"),
                WorkflowName.of("Daily ETL"),
                dag(),
                CREATED_AT);
    }

    private static DagDefinition dag() {
        return new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());
    }
}
