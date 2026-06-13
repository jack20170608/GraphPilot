package com.graphpilot.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.QueryWorkflowService;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryWorkflowServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-13T00:00:00Z");
    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");

    @Mock
    private WorkflowRepository workflowRepository;

    private QueryWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new QueryWorkflowService(workflowRepository);
    }

    @Test
    void findsWorkflowById() {
        Workflow workflow = workflow("workflow-1", "Daily ETL");
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));

        Workflow foundWorkflow = service.findById(WORKFLOW_ID);

        assertThat(foundWorkflow).isEqualTo(workflow);
        verify(workflowRepository).findById(WORKFLOW_ID);
    }

    @Test
    void throwsWhenWorkflowDoesNotExist() {
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(WORKFLOW_ID))
                .isInstanceOf(WorkflowNotFoundException.class)
                .hasMessage("Workflow not found: id=workflow-1");
        verify(workflowRepository).findById(WORKFLOW_ID);
    }

    @Test
    void rejectsNullWorkflowId() {
        assertThatThrownBy(() -> service.findById(null))
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
