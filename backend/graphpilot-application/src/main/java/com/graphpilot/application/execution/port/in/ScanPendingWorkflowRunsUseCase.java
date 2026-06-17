package com.graphpilot.application.execution.port.in;

import com.graphpilot.application.execution.ScanResult;

public interface ScanPendingWorkflowRunsUseCase {
    ScanResult scan(int limit);
}
