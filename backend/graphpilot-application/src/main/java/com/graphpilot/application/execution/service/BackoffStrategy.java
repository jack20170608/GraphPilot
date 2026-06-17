package com.graphpilot.application.execution.service;

/**
 * Strategy for pausing before retrying a failed task.
 *
 * <p>Kept framework-free so the worker core stays portable across runtimes.
 * Implementations decide how (and whether) to wait — for example a fixed
 * delay, exponential backoff, or a no-op in tests. Called inline by the
 * coordinator between a task's failure and its re-execution within the same
 * wave loop, so the delay must be bounded.
 */
public interface BackoffStrategy {

    /**
     * Block (or otherwise defer) before the given retry attempt is re-executed.
     *
     * @param attemptNumber the retry attempt about to start (1-based: the first
     *                      retry after the initial failure is attempt 1)
     */
    void awaitRetry(int attemptNumber);
}
