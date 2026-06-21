package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.admin.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.admin.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.admin.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.domain.workflow.WorkflowId;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
class WorkflowController {

    private static final int MAX_LIST_LIMIT = 100;

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final QueryWorkflowUseCase queryWorkflowUseCase;
    private final ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase;

    WorkflowController(
            CreateWorkflowUseCase createWorkflowUseCase,
            QueryWorkflowUseCase queryWorkflowUseCase,
            ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase) {
        this.createWorkflowUseCase = Objects.requireNonNull(
                createWorkflowUseCase,
                "createWorkflowUseCase must not be null");
        this.queryWorkflowUseCase = Objects.requireNonNull(
                queryWorkflowUseCase,
                "queryWorkflowUseCase must not be null");
        this.changeWorkflowLifecycleUseCase = Objects.requireNonNull(
                changeWorkflowLifecycleUseCase,
                "changeWorkflowLifecycleUseCase must not be null");
    }

    @PostMapping
    ResponseEntity<CreateWorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Workflow request body must not be null");
        }
        WorkflowId workflowId = createWorkflowUseCase.create(request.toCommand());
        URI location = URI.create("/api/workflows/" + workflowId.value());
        return ResponseEntity.created(location)
                .body(new CreateWorkflowResponse(workflowId.value()));
    }

    @GetMapping
    ResponseEntity<List<WorkflowResponse>> list(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        int boundedLimit = validateListLimit(limit);
        return ResponseEntity.ok(queryWorkflowUseCase.findAll(boundedLimit).stream()
                .map(WorkflowResponse::from)
                .toList());
    }

    private static int validateListLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException(
                    "Workflow list limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return limit;
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkflowResponse> getById(@PathVariable("id") String id) {
        WorkflowId workflowId = workflowIdFrom(id);
        return ResponseEntity.ok(WorkflowResponse.from(queryWorkflowUseCase.findById(workflowId)));
    }

    @PostMapping("/{id}/activate")
    ResponseEntity<WorkflowResponse> activate(@PathVariable("id") String id) {
        return ResponseEntity.ok(WorkflowResponse.from(
                changeWorkflowLifecycleUseCase.activate(workflowIdFrom(id))));
    }

    @PostMapping("/{id}/pause")
    ResponseEntity<WorkflowResponse> pause(@PathVariable("id") String id) {
        return ResponseEntity.ok(WorkflowResponse.from(
                changeWorkflowLifecycleUseCase.pause(workflowIdFrom(id))));
    }

    @PostMapping("/{id}/resume")
    ResponseEntity<WorkflowResponse> resume(@PathVariable("id") String id) {
        return ResponseEntity.ok(WorkflowResponse.from(
                changeWorkflowLifecycleUseCase.resume(workflowIdFrom(id))));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<WorkflowResponse> archive(@PathVariable("id") String id) {
        return ResponseEntity.ok(WorkflowResponse.from(
                changeWorkflowLifecycleUseCase.archive(workflowIdFrom(id))));
    }

    private static WorkflowId workflowIdFrom(String id) {
        return WorkflowId.of(UriUtils.decode(id, StandardCharsets.UTF_8));
    }
}
