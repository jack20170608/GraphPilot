package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRun;
import com.graphpilot.domain.execution.WorkflowRun;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Minimal HTTP API for the Micronaut PoC: create + activate a workflow, trigger
 * a run, and query the run and its tasks. Reuses the same framework-free use
 * cases as the Spring bootstrap — proving the core is host-agnostic.
 */
@Singleton
@Controller("/api")
final class WorkflowRunController {

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final ChangeWorkflowLifecycleUseCase lifecycleUseCase;
    private final TriggerWorkflowRunUseCase triggerWorkflowRunUseCase;
    private final QueryWorkflowRunUseCase queryWorkflowRunUseCase;

    WorkflowRunController(
            CreateWorkflowUseCase createWorkflowUseCase,
            ChangeWorkflowLifecycleUseCase lifecycleUseCase,
            TriggerWorkflowRunUseCase triggerWorkflowRunUseCase,
            QueryWorkflowRunUseCase queryWorkflowRunUseCase) {
        this.createWorkflowUseCase = createWorkflowUseCase;
        this.lifecycleUseCase = lifecycleUseCase;
        this.triggerWorkflowRunUseCase = triggerWorkflowRunUseCase;
        this.queryWorkflowRunUseCase = queryWorkflowRunUseCase;
    }

    @Post("/workflows")
    WorkflowResponse createWorkflow(@Body CreateWorkflowRequest request) {
        CreateWorkflowCommand command = new CreateWorkflowCommand(
                request.name(),
                request.tasks().stream()
                        .map(t -> new TaskDefinition(TaskId.of(t.id()), t.name(), t.type()))
                        .toList(),
                request.edges().stream()
                        .map(e -> new DagEdge(TaskId.of(e.fromTaskId()), TaskId.of(e.toTaskId())))
                        .toList());
        WorkflowId workflowId = createWorkflowUseCase.create(command);
        Workflow activated = lifecycleUseCase.activate(workflowId);
        return WorkflowResponse.from(activated);
    }

    @Post("/workflows/{workflowId}/runs")
    TriggerRunResponse triggerRun(@PathVariable String workflowId) {
        WorkflowRunId runId = triggerWorkflowRunUseCase.trigger(WorkflowId.of(workflowId));
        return new TriggerRunResponse(runId.value());
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

    record CreateWorkflowRequest(
            String name,
            List<TaskSpec> tasks,
            List<EdgeSpec> edges) {
    }

    record TaskSpec(String id, String name, String type) {
    }

    record EdgeSpec(String fromTaskId, String toTaskId) {
    }

    record WorkflowResponse(String id, String name, String status) {
        static WorkflowResponse from(Workflow workflow) {
            return new WorkflowResponse(workflow.id().value(), workflow.name().value(), workflow.status().name());
        }
    }

    record TriggerRunResponse(String id) {
    }

    record RunResponse(String id, String workflowId, String status) {
        static RunResponse from(WorkflowRun run) {
            return new RunResponse(run.id().value(), run.workflowId().value(), run.status().name());
        }
    }

    record TaskRunResponse(
            String taskId,
            String taskName,
            String taskType,
            String status,
            int retryCount,
            String output,
            String errorMessage) {
        static TaskRunResponse from(TaskRun taskRun) {
            return new TaskRunResponse(
                    taskRun.taskId().value(),
                    taskRun.taskName(),
                    taskRun.taskType(),
                    taskRun.status().name(),
                    taskRun.retryCount(),
                    taskRun.output(),
                    taskRun.errorMessage());
        }
    }
}
