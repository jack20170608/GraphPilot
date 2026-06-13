package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.workflow.port.out.ClockPort;
import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
