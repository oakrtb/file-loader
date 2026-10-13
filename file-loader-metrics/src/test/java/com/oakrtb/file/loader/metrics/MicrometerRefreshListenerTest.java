package com.oakrtb.file.loader.metrics;

import com.oakrtb.file.loader.store.RefreshResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerRefreshListenerTest {

    @Test
    void recordsSuccessFailureAndSkip() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRefreshListener listener = new MicrometerRefreshListener(registry);

        listener.onRefresh(RefreshResult.ok(Duration.ofMillis(12), 1, 1, 0, 0, 1, Instant.now()));
        listener.onRefresh(RefreshResult.failed(Duration.ofMillis(3), new RuntimeException("x")));
        listener.onRefresh(RefreshResult.skipped(Duration.ofMillis(1)));

        assertEquals(1.0, registry.counter("file.loader.refresh", "result", "success").count());
        assertEquals(1.0, registry.counter("file.loader.refresh", "result", "failure").count());
        assertEquals(1.0, registry.counter("file.loader.refresh", "result", "skipped").count());
        assertEquals(3, registry.timer("file.loader.refresh.duration").count());
    }
}
