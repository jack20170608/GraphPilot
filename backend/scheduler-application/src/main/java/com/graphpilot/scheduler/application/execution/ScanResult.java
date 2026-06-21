package com.graphpilot.scheduler.application.execution;

import java.util.List;

public record ScanResult(
        int scannedCount,
        int executedCount,
        int failedCount,
        List<ScanFailure> failures) {

    public ScanResult {
        failures = List.copyOf(failures);
    }
}