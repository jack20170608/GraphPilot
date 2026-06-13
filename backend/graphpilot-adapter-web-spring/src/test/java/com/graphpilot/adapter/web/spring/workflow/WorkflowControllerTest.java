package com.graphpilot.adapter.web.spring.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.domain.dag.DagValidationException;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.WorkflowId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkflowController.class)
@ContextConfiguration(classes = {WorkflowController.class, WorkflowHttpExceptionHandler.class})
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateWorkflowUseCase createWorkflowUseCase;

    @Test
    void createsWorkflow() throws Exception {
        when(createWorkflowUseCase.create(any(CreateWorkflowCommand.class)))
                .thenReturn(WorkflowId.of("workflow-1"));
        String requestBody = """
                {
                  "name": "Daily ETL",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "load" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/workflows/workflow-1"))
                .andExpect(jsonPath("$.id").value("workflow-1"));

        ArgumentCaptor<CreateWorkflowCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateWorkflowCommand.class);
        verify(createWorkflowUseCase).create(commandCaptor.capture());
        CreateWorkflowCommand command = commandCaptor.getValue();
        assertThat(command.name()).isEqualTo("Daily ETL");
        assertThat(command.tasks()).hasSize(2);
        assertThat(command.tasks().getFirst().id()).isEqualTo(TaskId.of("extract"));
        assertThat(command.tasks().getFirst().name()).isEqualTo("Extract data");
        assertThat(command.edges()).hasSize(1);
        assertThat(command.edges().getFirst().fromTaskId()).isEqualTo(TaskId.of("extract"));
        assertThat(command.edges().getFirst().toTaskId()).isEqualTo(TaskId.of("load"));
    }

    @Test
    void returnsBadRequestWhenDagIsInvalid() throws Exception {
        when(createWorkflowUseCase.create(any(CreateWorkflowCommand.class)))
                .thenThrow(new DagValidationException("DAG contains a cycle"));
        String requestBody = """
                {
                  "name": "Invalid workflow",
                  "tasks": [
                    { "id": "extract", "name": "Extract data" },
                    { "id": "load", "name": "Load data" }
                  ],
                  "edges": [
                    { "fromTaskId": "extract", "toTaskId": "load" },
                    { "fromTaskId": "load", "toTaskId": "extract" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request failed validation"));
    }

    @Test
    void returnsBadRequestWhenTaskEntryIsNull() throws Exception {
        String requestBody = """
                {
                  "name": "Invalid workflow",
                  "tasks": [null],
                  "edges": []
                }
                """;

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request body is invalid"));
    }

    @Test
    void returnsBadRequestWhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request body is malformed"));
    }

    @Test
    void returnsBadRequestWhenRequestBodyIsNullLiteral() throws Exception {
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request body is malformed"));
    }
}
