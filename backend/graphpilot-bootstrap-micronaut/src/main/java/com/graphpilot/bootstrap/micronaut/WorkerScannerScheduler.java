package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
final class WorkerScannerScheduler {

    private final ScanPendingWorkflowRunsUseCase scanner;
    private final boolean enabled;
    private final int limit;

    WorkerScannerScheduler(
            ScanPendingWorkflowRunsUseCase scanner,
            @Value("${graphpilot.worker.scanner.enabled:true}") boolean enabled,
            @Value("${graphpilot.worker.scanner.limit:20}") int limit) {
        this.scanner = scanner;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelay = "${graphpilot.worker.scanner.interval:10s}")
    void scan() {
        if (enabled) {
            scanner.scan(limit);
        }
    }
}
