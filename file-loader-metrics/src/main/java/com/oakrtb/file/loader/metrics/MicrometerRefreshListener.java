package com.oakrtb.file.loader.metrics;

import com.oakrtb.file.loader.store.RefreshListener;
import com.oakrtb.file.loader.store.RefreshResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Records refresh success / failure / skip counts and duration to Micrometer.
 */
public final class MicrometerRefreshListener implements RefreshListener {

    private final Counter success;
    private final Counter failure;
    private final Counter skipped;
    private final Timer duration;

    public MicrometerRefreshListener(MeterRegistry registry) {
        this(registry, "file.loader");
    }

    public MicrometerRefreshListener(MeterRegistry registry, String prefix) {
        Objects.requireNonNull(registry, "registry");
        String p = prefix == null || prefix.isBlank() ? "file.loader" : prefix;
        this.success = registry.counter(p + ".refresh", "result", "success");
        this.failure = registry.counter(p + ".refresh", "result", "failure");
        this.skipped = registry.counter(p + ".refresh", "result", "skipped");
        this.duration = registry.timer(p + ".refresh.duration");
    }

    @Override
    public void onRefresh(RefreshResult result) {
        duration.record(result.getDuration().toMillis(), TimeUnit.MILLISECONDS);
        if (result.isSkipped()) {
            skipped.increment();
        } else if (result.isSuccess()) {
            success.increment();
        } else {
            failure.increment();
        }
    }
}
