package com.graphpilot.adapter.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.domain.execution.TimelineEventId;
import com.graphpilot.domain.execution.TimelineEventType;
import com.graphpilot.domain.execution.WorkflowRunId;
import com.graphpilot.domain.execution.WorkflowRunTimelineEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowRunTimelineRepositoryTest {

    @Test
    void savesAndFindsEventsSortedByOccurredAtThenIdWithLimit() {
        var repository = new InMemoryWorkflowRunTimelineRepository();
        WorkflowRunId runId = WorkflowRunId.of("run-1");
        WorkflowRunTimelineEvent second = WorkflowRunTimelineEvent.runLevel(
                TimelineEventId.of("event-b"), runId, TimelineEventType.RUN_STARTED,
                "started", Instant.parse("2026-06-17T00:00:01Z"));
        WorkflowRunTimelineEvent first = WorkflowRunTimelineEvent.runLevel(
                TimelineEventId.of("event-a"), runId, TimelineEventType.RUN_CREATED,
                "created", Instant.parse("2026-06-17T00:00:00Z"));

        repository.save(second);
        repository.save(first);

        assertThat(repository.findByWorkflowRunId(runId, 10)).containsExactly(first, second);
        assertThat(repository.findByWorkflowRunId(runId, 1)).containsExactly(first);
    }

    @Test
    void rejectsNonPositiveLimit() {
        var repository = new InMemoryWorkflowRunTimelineRepository();

        assertThatThrownBy(() -> repository.findByWorkflowRunId(WorkflowRunId.of("run-1"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Timeline event query limit must be positive");
    }

    @Test
    void generatesUniqueTimelineEventIds() {
        UuidTimelineEventIdGenerator generator = new UuidTimelineEventIdGenerator();

        TimelineEventId first = generator.nextTimelineEventId();
        TimelineEventId second = generator.nextTimelineEventId();

        assertThat(first.value()).isNotBlank();
        assertThat(second.value()).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }
}
