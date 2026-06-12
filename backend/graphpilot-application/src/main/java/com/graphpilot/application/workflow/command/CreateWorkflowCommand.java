package com.graphpilot.application.workflow.command;

import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record CreateWorkflowCommand(
        String name,
        List<TaskDefinition> tasks,
        List<DagEdge> edges) {

    public CreateWorkflowCommand(
            String name,
            Collection<TaskDefinition> tasks,
            Collection<DagEdge> edges) {
        this(name, List.copyOf(tasks), List.copyOf(edges));
    }

    public CreateWorkflowCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workflow name must not be blank");
        }
        Objects.requireNonNull(tasks, "tasks must not be null");
        Objects.requireNonNull(edges, "edges must not be null");
        name = name.trim();
        tasks = List.copyOf(tasks);
        edges = List.copyOf(edges);
    }
}
