package com.graphpilot.adapter.persistence.memory;

import com.graphpilot.application.shared.port.ClockPort;
import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
