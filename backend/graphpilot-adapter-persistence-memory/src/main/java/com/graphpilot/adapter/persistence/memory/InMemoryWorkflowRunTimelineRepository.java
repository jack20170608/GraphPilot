package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryWorkflowRunTimelineRepository implements WorkflowRunTimelineRepository {

    private final ConcurrentMap<WorkflowRunId, List<WorkflowRunTimelineEvent>> events = new ConcurrentHashMap<>();

    @Override
    public WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.compute(event.workflowRunId(), (runId, existing) -> {
            List<WorkflowRunTimelineEvent> copy = new ArrayList<>(existing == null ? List.of() : existing);
            copy.add(event);
            return List.copyOf(copy);
        });
        return event;
    }

    @Override
    public List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Timeline event query limit must be positive");
        }
        return events.getOrDefault(workflowRunId, List.of()).stream()
                .sorted(Comparator.comparing(WorkflowRunTimelineEvent::occurredAt)
                        .thenComparing(event -> event.id().value()))
                .limit(limit)
                .toList();
    }
}
