package com.graphpilot.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.IdGeneratorPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.CreateWorkflowService;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.DagValidationException;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");
    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("workflow-1");

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private IdGeneratorPort idGenerator;

    @Mock
    private ClockPort clock;

    @Captor
    private ArgumentCaptor<Workflow> workflowCaptor;

    private CreateWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new CreateWorkflowService(workflowRepository, idGenerator, clock);
    }

    @Test
    void createsAndSavesWorkflowWithValidDag() {
        when(idGenerator.nextWorkflowId()).thenReturn(WORKFLOW_ID);
        when(clock.now()).thenReturn(NOW);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateWorkflowCommand command = new CreateWorkflowCommand(
                "Daily ETL",
                List.of(
                        new TaskDefinition(TaskId.of("extract"), "Extract data"),
                        new TaskDefinition(TaskId.of("load"), "Load data")),
                List.of(new DagEdge(TaskId.of("extract"), TaskId.of("load"))));

        WorkflowId workflowId = service.create(command);

        verify(workflowRepository).save(workflowCaptor.capture());
        Workflow savedWorkflow = workflowCaptor.getValue();
        assertThat(workflowId).isEqualTo(WORKFLOW_ID);
        assertThat(savedWorkflow.name().value()).isEqualTo("Daily ETL");
        assertThat(savedWorkflow.createdAt()).isEqualTo(NOW);
        assertThat(savedWorkflow.dag().topologicalTaskIds())
                .containsExactly(TaskId.of("extract"), TaskId.of("load"));
    }

    @Test
    void rejectsInvalidDagBeforeSavingWorkflow() {
        CreateWorkflowCommand command = new CreateWorkflowCommand(
                "Invalid workflow",
                List.of(
                        new TaskDefinition(TaskId.of("extract"), "Extract data"),
                        new TaskDefinition(TaskId.of("load"), "Load data")),
                List.of(
                        new DagEdge(TaskId.of("extract"), TaskId.of("load")),
                        new DagEdge(TaskId.of("load"), TaskId.of("extract"))));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DagValidationException.class)
                .hasMessage("DAG contains a cycle");
        verify(workflowRepository, never()).save(any());
    }
}
