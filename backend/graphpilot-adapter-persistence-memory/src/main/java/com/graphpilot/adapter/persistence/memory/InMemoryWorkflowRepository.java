package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryWorkflowRepository implements WorkflowRepository {

    private final ConcurrentMap<WorkflowId, Workflow> workflows = new ConcurrentHashMap<>();

    @Override
    public Workflow save(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        workflows.put(workflow.id(), workflow);
        return workflow;
    }

    @Override
    public Optional<Workflow> findById(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        return Optional.ofNullable(workflows.get(workflowId));
    }

    @Override
    public List<Workflow> findAll(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Workflow query limit must be positive");
        }
        return workflows.values().stream()
                .sorted(Comparator
                        .comparing(Workflow::createdAt)
                        .thenComparing(workflow -> workflow.id().value()))
                .limit(limit)
                .toList();
    }
}
