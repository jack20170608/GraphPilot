package com.graphpilot.bootstrap.spring;

import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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

    @Scheduled(fixedDelayString = "${graphpilot.worker.scanner.interval-ms:10000}")
    void scan() {
        if (enabled) {
            scanner.scan(limit);
        }
    }
}
