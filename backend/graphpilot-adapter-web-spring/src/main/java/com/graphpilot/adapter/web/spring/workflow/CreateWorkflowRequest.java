package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.application.workflow.command.CreateWorkflowCommand;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record CreateWorkflowRequest(
        @NotBlank @Size(max = 120) String name,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid TaskRequest> tasks,
        @NotNull @Size(max = 1000) List<@NotNull @Valid EdgeRequest> edges) {

    CreateWorkflowCommand toCommand() {
        if (tasks == null) {
            throw new IllegalArgumentException("Workflow tasks must not be null");
        }
        if (edges == null) {
            throw new IllegalArgumentException("Workflow edges must not be null");
        }
        return new CreateWorkflowCommand(
                name,
                tasks.stream().map(TaskRequest::toTaskDefinition).toList(),
                edges.stream().map(EdgeRequest::toDagEdge).toList());
    }

    record TaskRequest(
            @NotBlank @Size(max = 80) String id,
            @NotBlank @Size(max = 120) String name) {

        TaskDefinition toTaskDefinition() {
            return new TaskDefinition(TaskId.of(id), name);
        }
    }

    record EdgeRequest(
            @NotBlank @Size(max = 80) String fromTaskId,
            @NotBlank @Size(max = 80) String toTaskId) {

        DagEdge toDagEdge() {
            return new DagEdge(TaskId.of(fromTaskId), TaskId.of(toTaskId));
        }
    }
}
