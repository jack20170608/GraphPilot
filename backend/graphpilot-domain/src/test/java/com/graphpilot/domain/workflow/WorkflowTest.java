package com.graphpilot.domain.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void createsWorkflowWithValidValues() {
        DagDefinition dag = new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());

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
        DagDefinition dag = new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());

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
}
