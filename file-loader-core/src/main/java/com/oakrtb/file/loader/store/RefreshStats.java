package com.oakrtb.file.loader.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cumulative refresh counters for ops dashboards / health checks.
 */
public final class RefreshStats implements RefreshListener {

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong skippedCount = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final AtomicLong lastDownloaded = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicReference<RefreshResult> lastResult = new AtomicReference<>();

    @Override
    public void onRefresh(RefreshResult result) {
        lastResult.set(result);
        totalDurationMs.addAndGet(result.getDuration().toMillis());
        if (result.isSkipped()) {
            skippedCount.incrementAndGet();
            return;
        }
        if (result.isSuccess()) {
            successCount.incrementAndGet();
            lastDownloaded.set(result.getDownloaded());
            lastSuccessAt.set(Instant.now());
        } else {
            failureCount.incrementAndGet();
            lastFailureAt.set(Instant.now());
        }
    }

    public long successCount() {
        return successCount.get();
    }

    public long failureCount() {
        return failureCount.get();
    }

    public long skippedCount() {
        return skippedCount.get();
    }

    public long totalDurationMs() {
        return totalDurationMs.get();
    }

    public long lastDownloaded() {
        return lastDownloaded.get();
    }

    public Optional<Instant> lastSuccessAt() {
        return Optional.ofNullable(lastSuccessAt.get());
    }

    public Optional<Instant> lastFailureAt() {
        return Optional.ofNullable(lastFailureAt.get());
    }

    public Optional<RefreshResult> lastResult() {
        return Optional.ofNullable(lastResult.get());
    }

    public Duration averageDuration() {
        long total = successCount.get() + failureCount.get() + skippedCount.get();
        if (total == 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(totalDurationMs.get() / total);
    }
}
