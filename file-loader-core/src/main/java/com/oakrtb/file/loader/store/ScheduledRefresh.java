package com.oakrtb.file.loader.store;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically calls {@link InMemoryStore#refresh()}. Failures keep the previous snapshot.
 * Store listeners already record detailed stats; this class only schedules.
 */
public final class ScheduledRefresh implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ScheduledRefresh.class.getName());

    private final InMemoryStore store;
    private final Duration interval;
    private final boolean refreshImmediately;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> future;

    public ScheduledRefresh(InMemoryStore store, Duration interval, boolean refreshImmediately) {
        this.store = Objects.requireNonNull(store, "store");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.refreshImmediately = refreshImmediately;
        this.executor = Executors.newSingleThreadScheduledExecutor(daemonFactory("file-loader-refresh"));
    }

    public Duration interval() {
        return interval;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long delayMs = refreshImmediately ? 0L : interval.toMillis();
        future = executor.scheduleWithFixedDelay(this::safeRefresh, delayMs, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        ScheduledFuture<?> f = future;
        if (f != null) {
            f.cancel(false);
        }
        started.set(false);
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    private void safeRefresh() {
        try {
            store.refresh();
        } catch (Exception e) {
            // DefaultInMemoryStore already notifies listeners on failure before rethrowing.
            LOG.log(Level.FINE, "scheduled refresh error already reported via listeners", e);
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
