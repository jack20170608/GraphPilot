package com.graphpilot.adapter.web.spring.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.graphpilot.application.workflow.WorkflowNotFoundException;
import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.DagValidationException;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowLifecycleException;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkflowControllerTest {

    private MockMvc mockMvc;
    private CreateWorkflowUseCase createWorkflowUseCase;
    private QueryWorkflowUseCase queryWorkflowUseCase;
    private RecordingChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase;

    @BeforeEach
    void setUp() {
        createWorkflowUseCase = mock(CreateWorkflowUseCase.class);
        queryWorkflowUseCase = mock(QueryWorkflowUseCase.class);
        changeWorkflowLifecycleUseCase = new RecordingChangeWorkflowLifecycleUseCase();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkflowController(
                        createWorkflowUseCase,
                        queryWorkflowUseCase,
                        changeWorkflowLifecycleUseCase))
                .setControllerAdvice(new WorkflowHttpExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

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

    @Test
    void getsWorkflowByIdWithStatus() throws Exception {
        Workflow workflow = workflow("workflow-1", "Daily ETL", WorkflowStatus.DRAFT);
        when(queryWorkflowUseCase.findById(WorkflowId.of("workflow-1"))).thenReturn(workflow);

        mockMvc.perform(get("/api/workflows/workflow-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.name").value("Daily ETL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.tasks[0].id").value("extract"))
                .andExpect(jsonPath("$.tasks[0].name").value("Extract data"))
                .andExpect(jsonPath("$.tasks[1].id").value("load"))
                .andExpect(jsonPath("$.tasks[1].name").value("Load data"))
                .andExpect(jsonPath("$.edges[0].fromTaskId").value("extract"))
                .andExpect(jsonPath("$.edges[0].toTaskId").value("load"))
                .andExpect(jsonPath("$.createdAt").value("2026-06-13T00:00:00Z"));

        verify(queryWorkflowUseCase).findById(WorkflowId.of("workflow-1"));
    }

    @Test
    void returnsNotFoundWhenWorkflowDoesNotExist() throws Exception {
        WorkflowId workflowId = WorkflowId.of("missing-workflow");
        when(queryWorkflowUseCase.findById(workflowId))
                .thenThrow(new WorkflowNotFoundException(workflowId));

        mockMvc.perform(get("/api/workflows/missing-workflow"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workflow not found"))
                .andExpect(jsonPath("$.detail").value("Workflow was not found"));
    }

    @Test
    void returnsBadRequestWhenWorkflowIdIsBlank() throws Exception {
        mockMvc.perform(get("/api/workflows/%20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request failed validation"));
    }

    @Test
    void listsWorkflowsWithStatus() throws Exception {
        Workflow workflow1 = workflow("workflow-1", "Daily ETL", WorkflowStatus.DRAFT);
        Workflow workflow2 = workflow("workflow-2", "Weekly Report", WorkflowStatus.ACTIVE);
        when(queryWorkflowUseCase.findAll(50)).thenReturn(List.of(workflow1, workflow2));

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("workflow-1"))
                .andExpect(jsonPath("$[0].name").value("Daily ETL"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].tasks[0].id").value("extract"))
                .andExpect(jsonPath("$[0].tasks[0].name").value("Extract data"))
                .andExpect(jsonPath("$[0].tasks[1].id").value("load"))
                .andExpect(jsonPath("$[0].tasks[1].name").value("Load data"))
                .andExpect(jsonPath("$[0].edges[0].fromTaskId").value("extract"))
                .andExpect(jsonPath("$[0].edges[0].toTaskId").value("load"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-13T00:00:00Z"))
                .andExpect(jsonPath("$[1].id").value("workflow-2"))
                .andExpect(jsonPath("$[1].name").value("Weekly Report"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].tasks[0].id").value("extract"))
                .andExpect(jsonPath("$[1].tasks[0].name").value("Extract data"))
                .andExpect(jsonPath("$[1].edges[0].fromTaskId").value("extract"))
                .andExpect(jsonPath("$[1].edges[0].toTaskId").value("load"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-06-13T00:00:00Z"));

        verify(queryWorkflowUseCase).findAll(50);
    }

    @Test
    void listsNoWorkflowsWhenRepositoryIsEmpty() throws Exception {
        when(queryWorkflowUseCase.findAll(50)).thenReturn(List.of());

        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(queryWorkflowUseCase).findAll(50);
    }

    @Test
    void listsWorkflowsWithCustomLimit() throws Exception {
        when(queryWorkflowUseCase.findAll(2)).thenReturn(List.of());

        mockMvc.perform(get("/api/workflows?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(queryWorkflowUseCase).findAll(2);
    }

    @Test
    void returnsBadRequestWhenListLimitIsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/workflows?limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow request"))
                .andExpect(jsonPath("$.detail").value("Workflow request failed validation"));
    }

    @Test
    void activatesWorkflow() throws Exception {
        changeWorkflowLifecycleUseCase.save(workflow("workflow-1", "Daily ETL", WorkflowStatus.DRAFT));

        mockMvc.perform(post("/api/workflows/workflow-1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(changeWorkflowLifecycleUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
        assertThat(changeWorkflowLifecycleUseCase.lastAction()).isEqualTo("activate");
    }

    @Test
    void pausesWorkflow() throws Exception {
        changeWorkflowLifecycleUseCase.save(workflow("workflow-1", "Daily ETL", WorkflowStatus.ACTIVE));

        mockMvc.perform(post("/api/workflows/workflow-1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("PAUSED"));

        assertThat(changeWorkflowLifecycleUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
        assertThat(changeWorkflowLifecycleUseCase.lastAction()).isEqualTo("pause");
    }

    @Test
    void resumesWorkflow() throws Exception {
        changeWorkflowLifecycleUseCase.save(workflow("workflow-1", "Daily ETL", WorkflowStatus.PAUSED));

        mockMvc.perform(post("/api/workflows/workflow-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(changeWorkflowLifecycleUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
        assertThat(changeWorkflowLifecycleUseCase.lastAction()).isEqualTo("resume");
    }

    @Test
    void archivesWorkflow() throws Exception {
        changeWorkflowLifecycleUseCase.save(workflow("workflow-1", "Daily ETL", WorkflowStatus.ACTIVE));

        mockMvc.perform(post("/api/workflows/workflow-1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        assertThat(changeWorkflowLifecycleUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
        assertThat(changeWorkflowLifecycleUseCase.lastAction()).isEqualTo("archive");
    }

    @Test
    void returnsNotFoundWhenLifecycleWorkflowDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/workflows/missing-workflow/activate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workflow not found"))
                .andExpect(jsonPath("$.detail").value("Workflow was not found"));
    }

    @Test
    void returnsConflictWhenLifecycleTransitionIsInvalid() throws Exception {
        changeWorkflowLifecycleUseCase.save(workflow("workflow-1", "Daily ETL", WorkflowStatus.DRAFT));

        mockMvc.perform(post("/api/workflows/workflow-1/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid workflow lifecycle transition"))
                .andExpect(jsonPath("$.detail")
                        .value("Cannot pause workflow from status DRAFT"));
    }

    private static Workflow workflow(String id, String name, WorkflowStatus status) {
        return Workflow.restore(
                WorkflowId.of(id),
                WorkflowName.of(name),
                dag(),
                status,
                Instant.parse("2026-06-13T00:00:00Z"));
    }

    private static DagDefinition dag() {
        return new DagDefinition(
                List.of(
                        new TaskDefinition(TaskId.of("extract"), "Extract data"),
                        new TaskDefinition(TaskId.of("load"), "Load data")),
                List.of(new DagEdge(TaskId.of("extract"), TaskId.of("load"))));
    }

    private static final class RecordingChangeWorkflowLifecycleUseCase
            implements ChangeWorkflowLifecycleUseCase {

        private final Map<WorkflowId, Workflow> workflows = new HashMap<>();
        private WorkflowId lastWorkflowId;
        private String lastAction;

        void save(Workflow workflow) {
            workflows.put(workflow.id(), workflow);
        }

        WorkflowId lastWorkflowId() {
            return lastWorkflowId;
        }

        String lastAction() {
            return lastAction;
        }

        @Override
        public Workflow activate(WorkflowId workflowId) {
            return changeStatus(workflowId, "activate", Workflow::activate);
        }

        @Override
        public Workflow pause(WorkflowId workflowId) {
            return changeStatus(workflowId, "pause", Workflow::pause);
        }

        @Override
        public Workflow resume(WorkflowId workflowId) {
            return changeStatus(workflowId, "resume", Workflow::resume);
        }

        @Override
        public Workflow archive(WorkflowId workflowId) {
            return changeStatus(workflowId, "archive", Workflow::archive);
        }

        private Workflow changeStatus(
                WorkflowId workflowId,
                String action,
                WorkflowLifecycleAction workflowLifecycleAction) {
            lastWorkflowId = workflowId;
            lastAction = action;
            Workflow workflow = workflows.get(workflowId);
            if (workflow == null) {
                throw new WorkflowNotFoundException(workflowId);
            }
            Workflow changedWorkflow = workflowLifecycleAction.change(Objects.requireNonNull(workflow));
            workflows.put(workflowId, changedWorkflow);
            return changedWorkflow;
        }
    }

    @FunctionalInterface
    private interface WorkflowLifecycleAction {
        Workflow change(Workflow workflow) throws WorkflowLifecycleException;
    }
}
