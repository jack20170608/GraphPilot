package com.graphpilot.domain.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRunTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-12T00:00:00Z");
    private static final Instant TRIGGERED_AT = Instant.parse("2026-06-14T00:00:00Z");

    @Test
    void createsPendingRunForActiveWorkflow() {
        Workflow workflow = workflow().activate();

        WorkflowRun run = WorkflowRun.create(
                WorkflowRunId.of("run-1"),
                workflow,
                TRIGGERED_AT);

        assertEquals(WorkflowRunId.of("run-1"), run.id());
        assertEquals(workflow.id(), run.workflowId());
        assertEquals(WorkflowRunStatus.PENDING, run.status());
        assertEquals(TRIGGERED_AT, run.triggeredAt());
    }

    @Test
    void rejectsNonActiveWorkflowTrigger() {
        Workflow workflow = workflow();

        WorkflowRunTriggerException exception = assertThrows(
                WorkflowRunTriggerException.class,
                () -> WorkflowRun.create(WorkflowRunId.of("run-1"), workflow, TRIGGERED_AT));

        assertEquals("Workflow run can only be triggered for ACTIVE workflow", exception.getMessage());
    }

    @Test
    void rejectsMissingRequiredFields() {
        Workflow workflow = workflow().activate();

        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.create(null, workflow, TRIGGERED_AT));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.create(WorkflowRunId.of("run-1"), null, TRIGGERED_AT));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.create(WorkflowRunId.of("run-1"), workflow, null));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.restore(null, workflow.id(), WorkflowRunStatus.PENDING, TRIGGERED_AT));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.restore(WorkflowRunId.of("run-1"), null, WorkflowRunStatus.PENDING, TRIGGERED_AT));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.restore(WorkflowRunId.of("run-1"), workflow.id(), null, TRIGGERED_AT));
        assertThrows(
                NullPointerException.class,
                () -> WorkflowRun.restore(WorkflowRunId.of("run-1"), workflow.id(), WorkflowRunStatus.PENDING, null));
    }

    @Test
    void workflowRunIdTrimsAndRejectsBlank() {
        assertEquals("run-1", WorkflowRunId.of(" run-1 ").value());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowRunId.of(" "));

        assertEquals("Workflow run id must not be blank", exception.getMessage());
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
