package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.scheduler.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.domain.execution.TimelineEventId;
import java.util.UUID;

public final class UuidTimelineEventIdGenerator implements TimelineEventIdGeneratorPort {

    @Override
    public TimelineEventId nextTimelineEventId() {
        return TimelineEventId.of(UUID.randomUUID().toString());
    }
}
