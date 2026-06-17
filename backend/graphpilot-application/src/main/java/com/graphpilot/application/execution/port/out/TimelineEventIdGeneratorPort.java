package com.graphpilot.application.execution.port.out;

import com.graphpilot.domain.execution.TimelineEventId;

public interface TimelineEventIdGeneratorPort {
    TimelineEventId nextTimelineEventId();
}
