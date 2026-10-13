package com.oakrtb.file.loader.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one {@link InMemoryStore#refresh()} call.
 */
public final class RefreshResult {

    private final boolean success;
    private final boolean skipped;
    private final Duration duration;
    private final int listed;
    private final int downloaded;
    private final int reused;
    private final int failedFiles;
    private final int entryCount;
    private final Instant loadedAt;
    private final Throwable error;

    private RefreshResult(
            boolean success,
            boolean skipped,
            Duration duration,
            int listed,
            int downloaded,
            int reused,
            int failedFiles,
            int entryCount,
            Instant loadedAt,
            Throwable error) {
        this.success = success;
        this.skipped = skipped;
        this.duration = Objects.requireNonNull(duration, "duration");
        this.listed = listed;
        this.downloaded = downloaded;
        this.reused = reused;
        this.failedFiles = failedFiles;
        this.entryCount = entryCount;
        this.loadedAt = loadedAt;
        this.error = error;
    }

    public static RefreshResult skipped(Duration duration) {
        return new RefreshResult(true, true, duration, 0, 0, 0, 0, 0, null, null);
    }

    public static RefreshResult ok(
            Duration duration,
            int listed,
            int downloaded,
            int reused,
            int failedFiles,
            int entryCount,
            Instant loadedAt) {
        return new RefreshResult(
                true, false, duration, listed, downloaded, reused, failedFiles, entryCount, loadedAt, null);
    }

    public static RefreshResult failed(Duration duration, Throwable error) {
        return new RefreshResult(false, false, duration, 0, 0, 0, 0, 0, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public Duration getDuration() {
        return duration;
    }

    public int getListed() {
        return listed;
    }

    public int getDownloaded() {
        return downloaded;
    }

    public int getReused() {
        return reused;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public Optional<Instant> getLoadedAt() {
        return Optional.ofNullable(loadedAt);
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public String toString() {
        return "RefreshResult{success=" + success
                + ", skipped=" + skipped
                + ", duration=" + duration
                + ", listed=" + listed
                + ", downloaded=" + downloaded
                + ", reused=" + reused
                + ", failedFiles=" + failedFiles
                + ", entryCount=" + entryCount
                + ", loadedAt=" + loadedAt
                + ", error=" + (error == null ? null : error.getClass().getSimpleName())
                + '}';
    }
}
