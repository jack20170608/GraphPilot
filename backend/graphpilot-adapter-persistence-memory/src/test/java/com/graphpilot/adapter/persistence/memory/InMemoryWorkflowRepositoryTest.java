package com.graphpilot.adapter.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-13T00:00:00Z");

    private final InMemoryWorkflowRepository repository = new InMemoryWorkflowRepository();

    @Test
    void savesAndFindsWorkflowById() {
        Workflow workflow = workflow("workflow-1", "Daily ETL");

        Workflow savedWorkflow = repository.save(workflow);

        assertThat(savedWorkflow).isEqualTo(workflow);
        assertThat(repository.findById(workflow.id())).contains(workflow);
    }

    @Test
    void returnsEmptyWhenWorkflowDoesNotExist() {
        assertThat(repository.findById(WorkflowId.of("missing-workflow"))).isEmpty();
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workflow must not be null");
        assertThatThrownBy(() -> repository.findById(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workflowId must not be null");
    }

    private static Workflow workflow(String id, String name) {
        DagDefinition dag = new DagDefinition(
                List.of(new TaskDefinition(TaskId.of("extract"), "Extract data")),
                List.of());
        return Workflow.create(
                WorkflowId.of(id),
                WorkflowName.of(name),
                dag,
                CREATED_AT);
    }
}
