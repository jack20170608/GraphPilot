package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.scheduler.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.scheduler.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.workflow.WorkflowId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api")
class WorkflowRunController {

    private static final int MAX_LIST_LIMIT = 100;
    private static final int MAX_TIMELINE_LIMIT = 500;

    private final TriggerWorkflowRunUseCase triggerWorkflowRunUseCase;
    private final QueryWorkflowRunUseCase queryWorkflowRunUseCase;

    WorkflowRunController(
            TriggerWorkflowRunUseCase triggerWorkflowRunUseCase,
            QueryWorkflowRunUseCase queryWorkflowRunUseCase) {
        this.triggerWorkflowRunUseCase = Objects.requireNonNull(
                triggerWorkflowRunUseCase,
                "triggerWorkflowRunUseCase must not be null");
        this.queryWorkflowRunUseCase = Objects.requireNonNull(
                queryWorkflowRunUseCase,
                "queryWorkflowRunUseCase must not be null");
    }

    @PostMapping("/workflows/{workflowId}/runs")
    ResponseEntity<CreateWorkflowRunResponse> create(@PathVariable("workflowId") String workflowId) {
        WorkflowRunId workflowRunId = triggerWorkflowRunUseCase.trigger(workflowIdFrom(workflowId));
        URI location = URI.create("/api/workflow-runs/" + workflowRunId.value());
        return ResponseEntity.created(location)
                .body(new CreateWorkflowRunResponse(workflowRunId.value()));
    }

    @GetMapping("/workflows/{workflowId}/runs")
    ResponseEntity<List<WorkflowRunResponse>> listByWorkflowId(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        int boundedLimit = validateListLimit(limit);
        return ResponseEntity.ok(queryWorkflowRunUseCase
                .findRunsByWorkflowId(workflowIdFrom(workflowId), boundedLimit)
                .stream()
                .map(WorkflowRunResponse::from)
                .toList());
    }

    @GetMapping("/workflow-runs/{runId}")
    ResponseEntity<WorkflowRunResponse> getById(@PathVariable("runId") String runId) {
        return ResponseEntity.ok(WorkflowRunResponse.from(
                queryWorkflowRunUseCase.findRunById(workflowRunIdFrom(runId))));
    }

    @GetMapping("/workflow-runs/{runId}/tasks")
    ResponseEntity<List<TaskRunResponse>> listTaskRuns(@PathVariable("runId") String runId) {
        return ResponseEntity.ok(queryWorkflowRunUseCase
                .findTaskRunsByRunId(workflowRunIdFrom(runId))
                .stream()
                .map(TaskRunResponse::from)
                .toList());
    }

    @GetMapping("/workflow-runs/{runId}/timeline")
    ResponseEntity<List<TimelineEventResponse>> listTimeline(
            @PathVariable("runId") String runId,
            @RequestParam(name = "limit", defaultValue = "200") int limit) {
        int boundedLimit = validateTimelineLimit(limit);
        return ResponseEntity.ok(queryWorkflowRunUseCase
                .findTimelineByRunId(workflowRunIdFrom(runId), boundedLimit)
                .stream()
                .map(TimelineEventResponse::from)
                .toList());
    }

    private static int validateListLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException(
                    "Workflow run list limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return limit;
    }

    private static int validateTimelineLimit(int limit) {
        if (limit <= 0 || limit > MAX_TIMELINE_LIMIT) {
            throw new IllegalArgumentException(
                    "Timeline event query limit must be between 1 and " + MAX_TIMELINE_LIMIT);
        }
        return limit;
    }

    private static WorkflowId workflowIdFrom(String id) {
        return WorkflowId.of(UriUtils.decode(id, StandardCharsets.UTF_8));
    }

    private static WorkflowRunId workflowRunIdFrom(String id) {
        return WorkflowRunId.of(UriUtils.decode(id, StandardCharsets.UTF_8));
    }
}
