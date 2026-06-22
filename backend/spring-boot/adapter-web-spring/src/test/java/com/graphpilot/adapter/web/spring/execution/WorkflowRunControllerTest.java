package com.graphpilot.adapter.web.spring.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.graphpilot.scheduler.application.execution.WorkflowRunNotFoundException;
import com.graphpilot.scheduler.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.shared.exception.WorkflowNotFoundException;
import com.graphpilot.scheduler.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TaskRunStatus;
import com.graphpilot.domain.execution.TimelineEventId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import com.graphpilot.domain.execution.WorkflowRunStatus;
import com.graphpilot.domain.execution.WorkflowRunTriggerException;
import com.graphpilot.domain.workflow.WorkflowId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkflowRunControllerTest {

    private MockMvc mockMvc;
    private RecordingTriggerWorkflowRunUseCase triggerWorkflowRunUseCase;
    private RecordingQueryWorkflowRunUseCase queryWorkflowRunUseCase;

    @BeforeEach
    void setUp() {
        triggerWorkflowRunUseCase = new RecordingTriggerWorkflowRunUseCase();
        queryWorkflowRunUseCase = new RecordingQueryWorkflowRunUseCase();
        var objectMapper = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(JavaTimeModule.class)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkflowRunController(
                        triggerWorkflowRunUseCase,
                        queryWorkflowRunUseCase))
                .setControllerAdvice(new WorkflowRunHttpExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createsWorkflowRun() throws Exception {
        triggerWorkflowRunUseCase.save(WorkflowId.of("workflow-1"), WorkflowRunId.of("run-1"));

        mockMvc.perform(post("/api/workflows/workflow-1/runs"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/workflow-runs/run-1"))
                .andExpect(jsonPath("$.id").value("run-1"));

        assertThat(triggerWorkflowRunUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
    }

    @Test
    void returnsNotFoundWhenTriggerWorkflowDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/workflows/missing-workflow/runs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workflow run not found"))
                .andExpect(jsonPath("$.detail").value("Workflow run was not found"));
    }

    @Test
    void returnsConflictWhenWorkflowRunCannotBeTriggered() throws Exception {
        triggerWorkflowRunUseCase.saveFailure(
                WorkflowId.of("workflow-1"),
                new WorkflowRunTriggerException("Workflow run can only be triggered for ACTIVE workflow"));

        mockMvc.perform(post("/api/workflows/workflow-1/runs"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Workflow run cannot be triggered"))
                .andExpect(jsonPath("$.detail")
                        .value("Workflow run can only be triggered for ACTIVE workflow"));
    }

    @Test
    void listsWorkflowRunsForWorkflow() throws Exception {
        queryWorkflowRunUseCase.saveRuns(
                WorkflowId.of("workflow-1"),
                List.of(
                        workflowRun("run-1", "workflow-1", WorkflowRunStatus.PENDING),
                        workflowRun("run-2", "workflow-1", WorkflowRunStatus.RUNNING)));

        mockMvc.perform(get("/api/workflows/workflow-1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("run-1"))
                .andExpect(jsonPath("$[0].workflowId").value("workflow-1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].triggeredAt").value("2026-06-14T00:00:00Z"))
                .andExpect(jsonPath("$[1].id").value("run-2"))
                .andExpect(jsonPath("$[1].workflowId").value("workflow-1"))
                .andExpect(jsonPath("$[1].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].triggeredAt").value("2026-06-14T00:00:00Z"));

        assertThat(queryWorkflowRunUseCase.lastWorkflowId()).isEqualTo(WorkflowId.of("workflow-1"));
        assertThat(queryWorkflowRunUseCase.lastLimit()).isEqualTo(50);
    }

    @Test
    void listsWorkflowRunsWithCustomLimit() throws Exception {
        queryWorkflowRunUseCase.saveRuns(WorkflowId.of("workflow-1"), List.of());

        mockMvc.perform(get("/api/workflows/workflow-1/runs?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        assertThat(queryWorkflowRunUseCase.lastLimit()).isEqualTo(2);
    }

    @Test
    void getsWorkflowRunById() throws Exception {
        queryWorkflowRunUseCase.saveRun(workflowRun("run-1", "workflow-1", WorkflowRunStatus.SUCCEEDED));

        mockMvc.perform(get("/api/workflow-runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("run-1"))
                .andExpect(jsonPath("$.workflowId").value("workflow-1"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.triggeredAt").value("2026-06-14T00:00:00Z"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.finishedAt").doesNotExist());

        assertThat(queryWorkflowRunUseCase.lastWorkflowRunId()).isEqualTo(WorkflowRunId.of("run-1"));
    }

    @Test
    void taskRunsExposeExecutionDetail() throws Exception {
        TaskRun executed = TaskRun.restore(
                TaskRunId.of("task-run-1"),
                WorkflowRunId.of("run-1"),
                TaskId.of("extract"),
                "Extract data",
                "shell",
                TaskRunStatus.FAILED,
                0,
                2,
                3,
                "connection refused",
                null,
                Instant.parse("2026-06-14T00:00:05Z"),
                Instant.parse("2026-06-14T00:00:10Z"),
                Instant.parse("2026-06-14T00:00:00Z"));
        TaskRun succeeded = TaskRun.restore(
                TaskRunId.of("task-run-2"),
                WorkflowRunId.of("run-1"),
                TaskId.of("transform"),
                "Transform data",
                "mock",
                TaskRunStatus.SUCCEEDED,
                0,
                0,
                3,
                null,
                "transformed-payload",
                Instant.parse("2026-06-14T00:00:11Z"),
                Instant.parse("2026-06-14T00:00:12Z"),
                Instant.parse("2026-06-14T00:00:00Z"));
        queryWorkflowRunUseCase.saveTaskRuns(WorkflowRunId.of("run-1"), List.of(executed, succeeded));

        mockMvc.perform(get("/api/workflow-runs/run-1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskType").value("shell"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].retryCount").value(2))
                .andExpect(jsonPath("$[0].maxRetries").value(3))
                .andExpect(jsonPath("$[0].errorMessage").value("connection refused"))
                .andExpect(jsonPath("$[0].output").doesNotExist())
                .andExpect(jsonPath("$[0].startedAt").value("2026-06-14T00:00:05Z"))
                .andExpect(jsonPath("$[0].finishedAt").value("2026-06-14T00:00:10Z"))
                .andExpect(jsonPath("$[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[1].output").value("transformed-payload"));
    }

    @Test
    void listsTimelineForWorkflowRun() throws Exception {
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        queryWorkflowRunUseCase.saveRun(workflowRun("run-1", "workflow-1", WorkflowRunStatus.SUCCEEDED));
        queryWorkflowRunUseCase.saveTimeline(runId, List.of(
                WorkflowRunTimelineEvent.runLevel(
                        TimelineEventId.of("event-1"),
                        runId,
                        TimelineEventType.RUN_CREATED,
                        "Workflow run created",
                        Instant.parse("2026-06-14T00:00:00Z")),
                WorkflowRunTimelineEvent.taskLevel(
                        TimelineEventId.of("event-2"),
                        runId,
                        TaskRunId.of("task-run-1"),
                        TaskId.of("extract"),
                        TimelineEventType.TASK_SUCCEEDED,
                        "Task extract succeeded",
                        Instant.parse("2026-06-14T00:00:01Z"))));

        mockMvc.perform(get("/api/workflow-runs/run-1/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("event-1"))
                .andExpect(jsonPath("$[0].workflowRunId").value("run-1"))
                .andExpect(jsonPath("$[0].type").value("RUN_CREATED"))
                .andExpect(jsonPath("$[0].message").value("Workflow run created"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-06-14T00:00:00Z"))
                .andExpect(jsonPath("$[1].taskRunId").value("task-run-1"))
                .andExpect(jsonPath("$[1].taskId").value("extract"))
                .andExpect(jsonPath("$[1].type").value("TASK_SUCCEEDED"));
    }

    @Test
    void returnsNotFoundWhenWorkflowRunDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/workflow-runs/missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workflow run not found"))
                .andExpect(jsonPath("$.detail").value("Workflow run was not found"));
    }

    @Test
    void listsTaskRunsForWorkflowRun() throws Exception {
        queryWorkflowRunUseCase.saveTaskRuns(
                WorkflowRunId.of("run-1"),
                List.of(taskRun("task-run-1", "run-1", "extract", "Extract data", 0)));

        mockMvc.perform(get("/api/workflow-runs/run-1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("task-run-1"))
                .andExpect(jsonPath("$[0].workflowRunId").value("run-1"))
                .andExpect(jsonPath("$[0].taskId").value("extract"))
                .andExpect(jsonPath("$[0].taskName").value("Extract data"))
                .andExpect(jsonPath("$[0].taskType").value("mock"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].retryCount").value(0))
                .andExpect(jsonPath("$[0].maxRetries").value(3))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-14T00:00:00Z"));

        assertThat(queryWorkflowRunUseCase.lastWorkflowRunId()).isEqualTo(WorkflowRunId.of("run-1"));
    }

    @Test
    void returnsBadRequestWhenRunListLimitIsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/workflows/workflow-1/runs?limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid workflow run request"));
    }

    private static WorkflowRun workflowRun(
            String id,
            String workflowId,
            WorkflowRunStatus status) {
        return WorkflowRun.restore(
                WorkflowRunId.of(id),
                WorkflowId.of(workflowId),
                status,
                Instant.parse("2026-06-14T00:00:00Z"));
    }

    private static TaskRun taskRun(
            String id,
            String workflowRunId,
            String taskId,
            String taskName,
            int position) {
        return TaskRun.restore(
                TaskRunId.of(id),
                WorkflowRunId.of(workflowRunId),
                TaskId.of(taskId),
                taskName,
                TaskRunStatus.PENDING,
                position,
                Instant.parse("2026-06-14T00:00:00Z"));
    }

    private static final class RecordingTriggerWorkflowRunUseCase implements TriggerWorkflowRunUseCase {

        private final Map<WorkflowId, WorkflowRunId> runIds = new HashMap<>();
        private final Map<WorkflowId, RuntimeException> failures = new HashMap<>();
        private WorkflowId lastWorkflowId;

        void save(WorkflowId workflowId, WorkflowRunId workflowRunId) {
            runIds.put(workflowId, workflowRunId);
        }

        void saveFailure(WorkflowId workflowId, RuntimeException exception) {
            failures.put(workflowId, exception);
        }

        WorkflowId lastWorkflowId() {
            return lastWorkflowId;
        }

        @Override
        public WorkflowRunId trigger(WorkflowId workflowId) {
            lastWorkflowId = workflowId;
            RuntimeException failure = failures.get(workflowId);
            if (failure != null) {
                throw failure;
            }
            WorkflowRunId workflowRunId = runIds.get(workflowId);
            if (workflowRunId == null) {
                throw new WorkflowNotFoundException(workflowId);
            }
            return workflowRunId;
        }
    }

    private static final class RecordingQueryWorkflowRunUseCase implements QueryWorkflowRunUseCase {

        private final Map<WorkflowRunId, WorkflowRun> runsById = new HashMap<>();
        private final Map<WorkflowId, List<WorkflowRun>> runsByWorkflowId = new HashMap<>();
        private final Map<WorkflowRunId, List<TaskRun>> tasksByRunId = new HashMap<>();
        private final Map<WorkflowRunId, List<WorkflowRunTimelineEvent>> timelineByRunId = new HashMap<>();
        private WorkflowId lastWorkflowId;
        private WorkflowRunId lastWorkflowRunId;
        private int lastLimit;

        void saveRun(WorkflowRun workflowRun) {
            runsById.put(workflowRun.id(), workflowRun);
        }

        void saveRuns(WorkflowId workflowId, List<WorkflowRun> workflowRuns) {
            runsByWorkflowId.put(workflowId, List.copyOf(workflowRuns));
            workflowRuns.forEach(this::saveRun);
        }

        void saveTaskRuns(WorkflowRunId workflowRunId, List<TaskRun> taskRuns) {
            tasksByRunId.put(workflowRunId, List.copyOf(taskRuns));
        }

        void saveTimeline(WorkflowRunId workflowRunId, List<WorkflowRunTimelineEvent> timelineEvents) {
            timelineByRunId.put(workflowRunId, List.copyOf(timelineEvents));
        }

        WorkflowId lastWorkflowId() {
            return lastWorkflowId;
        }

        WorkflowRunId lastWorkflowRunId() {
            return lastWorkflowRunId;
        }

        int lastLimit() {
            return lastLimit;
        }

        @Override
        public WorkflowRun findRunById(WorkflowRunId workflowRunId) {
            lastWorkflowRunId = workflowRunId;
            WorkflowRun workflowRun = runsById.get(workflowRunId);
            if (workflowRun == null) {
                throw new WorkflowRunNotFoundException(workflowRunId);
            }
            return workflowRun;
        }

        @Override
        public List<WorkflowRun> findRunsByWorkflowId(WorkflowId workflowId, int limit) {
            lastWorkflowId = workflowId;
            lastLimit = limit;
            return runsByWorkflowId.getOrDefault(workflowId, List.of()).stream()
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId) {
            lastWorkflowRunId = workflowRunId;
            return new ArrayList<>(tasksByRunId.getOrDefault(workflowRunId, List.of()));
        }

        @Override
        public List<WorkflowRunTimelineEvent> findTimelineByRunId(WorkflowRunId workflowRunId, int limit) {
            lastWorkflowRunId = workflowRunId;
            lastLimit = limit;
            return timelineByRunId.getOrDefault(workflowRunId, List.of()).stream()
                    .limit(limit)
                    .toList();
        }
    }
}
