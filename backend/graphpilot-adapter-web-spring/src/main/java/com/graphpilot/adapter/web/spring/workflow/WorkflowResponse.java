package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.workflow.Workflow;
import java.time.Instant;
import java.util.List;

record WorkflowResponse(
        String id,
        String name,
        List<TaskResponse> tasks,
        List<EdgeResponse> edges,
        Instant createdAt) {

    static WorkflowResponse from(Workflow workflow) {
        return new WorkflowResponse(
                workflow.id().value(),
                workflow.name().value(),
                workflow.dag().tasks().stream()
                        .map(TaskResponse::from)
                        .toList(),
                workflow.dag().edges().stream()
                        .map(EdgeResponse::from)
                        .toList(),
                workflow.createdAt());
    }

    record TaskResponse(String id, String name) {

        static TaskResponse from(TaskDefinition task) {
            return new TaskResponse(task.id().value(), task.name());
        }
    }

    record EdgeResponse(String fromTaskId, String toTaskId) {

        static EdgeResponse from(DagEdge edge) {
            return new EdgeResponse(edge.fromTaskId().value(), edge.toTaskId().value());
        }
    }
}
