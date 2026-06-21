package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunTimelineMapper;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRunTimelineEventRow;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.execution.TaskRunId;
import com.graphpilot.domain.execution.TimelineEventId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public final class MyBatisWorkflowRunTimelineRepository implements WorkflowRunTimelineRepository {

    private final WorkflowRunTimelineMapper mapper;

    public MyBatisWorkflowRunTimelineRepository(WorkflowRunTimelineMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    @Transactional
    public WorkflowRunTimelineEvent save(WorkflowRunTimelineEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        mapper.insert(toRow(event));
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRunTimelineEvent> findByWorkflowRunId(WorkflowRunId workflowRunId, int limit) {
        Objects.requireNonNull(workflowRunId, "workflowRunId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Timeline event query limit must be positive");
        }
        return mapper.findByWorkflowRunId(workflowRunId.value(), limit).stream()
                .map(MyBatisWorkflowRunTimelineRepository::toDomain)
                .toList();
    }

    private static WorkflowRunTimelineEventRow toRow(WorkflowRunTimelineEvent event) {
        return new WorkflowRunTimelineEventRow(
                event.id().value(),
                event.workflowRunId().value(),
                event.taskRunId() == null ? null : event.taskRunId().value(),
                event.taskId() == null ? null : event.taskId().value(),
                event.type().name(),
                event.message(),
                event.occurredAt());
    }

    private static WorkflowRunTimelineEvent toDomain(WorkflowRunTimelineEventRow row) {
        return new WorkflowRunTimelineEvent(
                TimelineEventId.of(row.id()),
                WorkflowRunId.of(row.workflowRunId()),
                row.taskRunId() == null ? null : TaskRunId.of(row.taskRunId()),
                row.taskId() == null ? null : TaskId.of(row.taskId()),
                TimelineEventType.valueOf(row.type()),
                row.message(),
                row.occurredAt());
    }
}
