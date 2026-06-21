package com.graphpilot.application.shared.port;

import java.time.Instant;

/**
 * Port for getting the current time.
 * This interface is shared between admin and scheduler modules.
 */
@FunctionalInterface
public interface ClockPort {

    Instant now();
}