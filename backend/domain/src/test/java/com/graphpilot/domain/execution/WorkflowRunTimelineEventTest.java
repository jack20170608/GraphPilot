package com.graphpilot.domain.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.graphpilot.domain.dag.TaskId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkflowRunTimelineEventTest {

    @Test
    void createsRunLevelEvent() {
        WorkflowRunTimelineEvent event = WorkflowRunTimelineEvent.runLevel(
                TimelineEventId.of("event-1"),
                WorkflowRunId.of("run-1"),
                TimelineEventType.RUN_CREATED,
                "Workflow run created",
                Instant.parse("2026-06-17T00:00:00Z"));

        assertNull(event.taskRunId());
        assertNull(event.taskId());
        assertEquals("Workflow run created", event.message());
    }

    @Test
    void createsTaskLevelEvent() {
        WorkflowRunTimelineEvent event = WorkflowRunTimelineEvent.taskLevel(
                TimelineEventId.of("event-1"),
                WorkflowRunId.of("run-1"),
                TaskRunId.of("task-run-1"),
                TaskId.of("extract"),
                TimelineEventType.TASK_SUCCEEDED,
                "Task extract succeeded",
                Instant.parse("2026-06-17T00:00:00Z"));

        assertEquals(TaskRunId.of("task-run-1"), event.taskRunId());
        assertEquals(TaskId.of("extract"), event.taskId());
    }

    @Test
    void rejectsBlankMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowRunTimelineEvent.runLevel(
                        TimelineEventId.of("event-1"),
                        WorkflowRunId.of("run-1"),
                        TimelineEventType.RUN_CREATED,
                        " ",
                        Instant.parse("2026-06-17T00:00:00Z")));

        assertEquals("Timeline event message must not be blank", exception.getMessage());
    }
}
