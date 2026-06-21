package com.graphpilot.scheduler.application.execution.port.in;

import com.graphpilot.scheduler.application.execution.ScanResult;

/**
 * Use case for scanning and executing pending workflow runs.
 * This is called by the scheduler's polling mechanism.
 */
public interface ScanPendingWorkflowRunsUseCase {

    /**
     * Scan and execute pending workflow runs.
     *
     * @param limit maximum number of workflow runs to process
     * @return result of the scan including success/failure counts
     */
    ScanResult scan(int limit);
}