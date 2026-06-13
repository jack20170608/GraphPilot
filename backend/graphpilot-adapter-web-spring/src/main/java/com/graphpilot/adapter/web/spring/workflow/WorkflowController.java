package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.domain.workflow.WorkflowId;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
class WorkflowController {

    private final CreateWorkflowUseCase createWorkflowUseCase;
    private final QueryWorkflowUseCase queryWorkflowUseCase;

    WorkflowController(
            CreateWorkflowUseCase createWorkflowUseCase,
            QueryWorkflowUseCase queryWorkflowUseCase) {
        this.createWorkflowUseCase = Objects.requireNonNull(
                createWorkflowUseCase,
                "createWorkflowUseCase must not be null");
        this.queryWorkflowUseCase = Objects.requireNonNull(
                queryWorkflowUseCase,
                "queryWorkflowUseCase must not be null");
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

    @GetMapping("/{id}")
    ResponseEntity<WorkflowResponse> getById(@PathVariable("id") String id) {
        WorkflowId workflowId = WorkflowId.of(UriUtils.decode(id, StandardCharsets.UTF_8));
        return ResponseEntity.ok(WorkflowResponse.from(queryWorkflowUseCase.findById(workflowId)));
    }
}
