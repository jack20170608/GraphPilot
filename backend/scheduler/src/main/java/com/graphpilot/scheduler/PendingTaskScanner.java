package com.graphpilot.scheduler;

import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期扫描 PENDING 状态的任务并触发执行。
 * 用于补偿未通过事件触发的任务。
 */
@Component
final class PendingTaskScanner {

    private static final Logger log = LoggerFactory.getLogger(PendingTaskScanner.class);

    private final ScanPendingWorkflowRunsUseCase scanner;
    private final boolean enabled;
    private final int limit;

    PendingTaskScanner(
            ScanPendingWorkflowRunsUseCase scanner,
            @Value("${graphpilot.scheduler.scanner.enabled:true}") boolean enabled,
            @Value("${graphpilot.scheduler.scanner.limit:20}") int limit) {
        this.scanner = scanner;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${graphpilot.scheduler.scanner.interval-ms:10000}")
    void scan() {
        if (enabled) {
            log.debug("Scanning pending tasks...");
            scanner.scan(limit);
        }
    }
}