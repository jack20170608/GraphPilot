package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskConfig;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Singleton;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Micronaut REST API aligned with the Spring workflow/run API.
 */
@Singleton
@Controller("/api")
final class WorkflowRunController {

    private static final int MAX_LIST_LIMIT = 100;
    private static final int MAX_TIMELINE_LIMIT = 500;

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final QueryWorkflowUseCase queryWorkflowUseCase;
    private final ChangeWorkflowLifecycleUseCase lifecycleUseCase;
    private final TriggerWorkflowRunUseCase triggerWorkflowRunUseCase;
    private final QueryWorkflowRunUseCase queryWorkflowRunUseCase;

    WorkflowRunController(
            CreateWorkflowUseCase createWorkflowUseCase,
            QueryWorkflowUseCase queryWorkflowUseCase,
            ChangeWorkflowLifecycleUseCase lifecycleUseCase,
            TriggerWorkflowRunUseCase triggerWorkflowRunUseCase,
            QueryWorkflowRunUseCase queryWorkflowRunUseCase) {
        this.createWorkflowUseCase = createWorkflowUseCase;
        this.queryWorkflowUseCase = queryWorkflowUseCase;
        this.lifecycleUseCase = lifecycleUseCase;
        this.triggerWorkflowRunUseCase = triggerWorkflowRunUseCase;
        this.queryWorkflowRunUseCase = queryWorkflowRunUseCase;
    }

    @Post("/workflows")
    HttpResponse<CreateWorkflowResponse> createWorkflow(@Body CreateWorkflowRequest request) {
        WorkflowId workflowId = createWorkflowUseCase.create(request.toCommand());
        return HttpResponse.created(new CreateWorkflowResponse(workflowId.value()))
                .headers(headers -> headers.location(URI.create("/api/workflows/" + workflowId.value())));
    }

    @Get("/workflows")
    List<WorkflowResponse> listWorkflows(@QueryValue(defaultValue = "50") int limit) {
        int boundedLimit = validateListLimit(limit);
        return queryWorkflowUseCase.findAll(boundedLimit).stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    @Get("/workflows/{workflowId}")
    WorkflowResponse getWorkflow(@PathVariable String workflowId) {
        return WorkflowResponse.from(queryWorkflowUseCase.findById(WorkflowId.of(workflowId)));
    }

    @Post("/workflows/{workflowId}/activate")
    WorkflowResponse activateWorkflow(@PathVariable String workflowId) {
        return WorkflowResponse.from(lifecycleUseCase.activate(WorkflowId.of(workflowId)));
    }

    @Post("/workflows/{workflowId}/pause")
    WorkflowResponse pauseWorkflow(@PathVariable String workflowId) {
        return WorkflowResponse.from(lifecycleUseCase.pause(WorkflowId.of(workflowId)));
    }

    @Post("/workflows/{workflowId}/resume")
    WorkflowResponse resumeWorkflow(@PathVariable String workflowId) {
        return WorkflowResponse.from(lifecycleUseCase.resume(WorkflowId.of(workflowId)));
    }

    @Post("/workflows/{workflowId}/archive")
    WorkflowResponse archiveWorkflow(@PathVariable String workflowId) {
        return WorkflowResponse.from(lifecycleUseCase.archive(WorkflowId.of(workflowId)));
    }

    @Get("/workflows/{workflowId}/runs")
    List<RunResponse> listRuns(@PathVariable String workflowId, @QueryValue(defaultValue = "50") int limit) {
        int boundedLimit = validateListLimit(limit);
        return queryWorkflowRunUseCase.findRunsByWorkflowId(WorkflowId.of(workflowId), boundedLimit).stream()
                .map(RunResponse::from)
                .toList();
    }

    @Post("/workflows/{workflowId}/runs")
    HttpResponse<TriggerRunResponse> triggerRun(@PathVariable String workflowId) {
        WorkflowRunId runId = triggerWorkflowRunUseCase.trigger(WorkflowId.of(workflowId));
        return HttpResponse.created(new TriggerRunResponse(runId.value()))
                .headers(headers -> headers.location(URI.create("/api/workflow-runs/" + runId.value())));
    }

    @Get("/workflow-runs/{runId}")
    RunResponse getRun(@PathVariable String runId) {
        WorkflowRun run = queryWorkflowRunUseCase.findRunById(WorkflowRunId.of(runId));
        return RunResponse.from(run);
    }

    @Get("/workflow-runs/{runId}/tasks")
    List<TaskRunResponse> getTasks(@PathVariable String runId) {
        return queryWorkflowRunUseCase.findTaskRunsByRunId(WorkflowRunId.of(runId)).stream()
                .map(TaskRunResponse::from)
                .toList();
    }

    @Get("/workflow-runs/{runId}/timeline")
    List<TimelineEventResponse> getTimeline(
            @PathVariable String runId,
            @QueryValue(defaultValue = "200") int limit) {
        int boundedLimit = validateTimelineLimit(limit);
        return queryWorkflowRunUseCase.findTimelineByRunId(WorkflowRunId.of(runId), boundedLimit).stream()
                .map(TimelineEventResponse::from)
                .toList();
    }

    private static int validateListLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("List limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return limit;
    }

    private static int validateTimelineLimit(int limit) {
        if (limit <= 0 || limit > MAX_TIMELINE_LIMIT) {
            throw new IllegalArgumentException("Timeline event query limit must be between 1 and " + MAX_TIMELINE_LIMIT);
        }
        return limit;
    }

    record CreateWorkflowRequest(String name, List<TaskSpec> tasks, List<EdgeSpec> edges) {
        CreateWorkflowCommand toCommand() {
            return new CreateWorkflowCommand(
                    name,
                    tasks.stream().map(TaskSpec::toTaskDefinition).toList(),
                    edges.stream().map(EdgeSpec::toDagEdge).toList());
        }
    }

    record TaskSpec(String id, String name, String type, Map<String, Object> config) {
        TaskDefinition toTaskDefinition() {
            return new TaskDefinition(
                    TaskId.of(id),
                    name,
                    type,
                    config == null ? TaskConfig.empty() : TaskConfig.of(config));
        }
    }

    record EdgeSpec(String fromTaskId, String toTaskId) {
        DagEdge toDagEdge() {
            return new DagEdge(TaskId.of(fromTaskId), TaskId.of(toTaskId));
        }
    }

    record CreateWorkflowResponse(String id) {
    }

    record WorkflowResponse(
            String id,
            String name,
            String status,
            List<TaskResponse> tasks,
            List<EdgeResponse> edges,
            Instant createdAt) {
        static WorkflowResponse from(Workflow workflow) {
            return new WorkflowResponse(
                    workflow.id().value(),
                    workflow.name().value(),
                    workflow.status().name(),
                    workflow.dag().tasks().stream().map(TaskResponse::from).toList(),
                    workflow.dag().edges().stream().map(EdgeResponse::from).toList(),
                    workflow.createdAt());
        }
    }

    record TaskResponse(String id, String name, String type, Map<String, Object> config) {
        static TaskResponse from(TaskDefinition task) {
            return new TaskResponse(task.id().value(), task.name(), task.type(), task.config().asMap());
        }
    }

    record EdgeResponse(String fromTaskId, String toTaskId) {
        static EdgeResponse from(DagEdge edge) {
            return new EdgeResponse(edge.fromTaskId().value(), edge.toTaskId().value());
        }
    }

    record TriggerRunResponse(String id) {
    }

    record RunResponse(
            String id,
            String workflowId,
            String status,
            Instant triggeredAt,
            Instant startedAt,
            Instant finishedAt) {
        static RunResponse from(WorkflowRun run) {
            return new RunResponse(
                    run.id().value(),
                    run.workflowId().value(),
                    run.status().name(),
                    run.triggeredAt(),
                    run.startedAt(),
                    run.finishedAt());
        }
    }

    record TaskRunResponse(
            String id,
            String workflowRunId,
            String taskId,
            String taskName,
            String taskType,
            String status,
            int position,
            int retryCount,
            int maxRetries,
            String errorMessage,
            String output,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt) {
        static TaskRunResponse from(TaskRun taskRun) {
            return new TaskRunResponse(
                    taskRun.id().value(),
                    taskRun.workflowRunId().value(),
                    taskRun.taskId().value(),
                    taskRun.taskName(),
                    taskRun.taskType(),
                    taskRun.status().name(),
                    taskRun.position(),
                    taskRun.retryCount(),
                    taskRun.maxRetries(),
                    taskRun.errorMessage(),
                    taskRun.output(),
                    taskRun.startedAt(),
                    taskRun.finishedAt(),
                    taskRun.createdAt());
        }
    }

    record TimelineEventResponse(
            String id,
            String workflowRunId,
            String taskRunId,
            String taskId,
            String type,
            String message,
            Instant occurredAt) {
        static TimelineEventResponse from(WorkflowRunTimelineEvent event) {
            return new TimelineEventResponse(
                    event.id().value(),
                    event.workflowRunId().value(),
                    event.taskRunId() == null ? null : event.taskRunId().value(),
                    event.taskId() == null ? null : event.taskId().value(),
                    event.type().name(),
                    event.message(),
                    event.occurredAt());
        }
    }
}
