package com.graphpilot.admin.application.workflow.port.out;

import java.time.Instant;

@FunctionalInterface
public interface ClockPort {

    Instant now();
}