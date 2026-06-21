package com.graphpilot.scheduler.application.execution.service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-delay backoff: sleeps for a constant duration before every retry.
 *
 * <p>Suitable for the single-node MVP worker. The delay is bounded by the
 * configured duration; callers requiring exponential or jittered backoff can
 * provide their own {@link BackoffStrategy}.
 */
public final class FixedBackoffStrategy implements BackoffStrategy {

    private final Duration delay;

    public FixedBackoffStrategy(Duration delay) {
        if (Objects.requireNonNull(delay, "delay must not be null").isNegative()) {
            throw new IllegalArgumentException("Backoff delay must not be negative");
        }
        this.delay = delay;
    }

    @Override
    public void awaitRetry(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (delay.isZero()) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(delay.toNanos());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}