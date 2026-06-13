package com.graphpilot.adapter.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SystemClockAdapterTest {

    private final SystemClockAdapter clock = new SystemClockAdapter();

    @Test
    void returnsCurrentInstant() {
        Instant before = Instant.now();

        Instant now = clock.now();

        Instant after = Instant.now();
        assertThat(now).isBetween(before, after);
    }
}