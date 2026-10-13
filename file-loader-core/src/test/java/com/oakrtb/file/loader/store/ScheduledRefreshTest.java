package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledRefreshTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshesOnScheduleAndUpdatesView() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "v1", StandardCharsets.UTF_8);

        LocalFsContentSource source = new LocalFsContentSource(tempDir);
        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "conf");
        RefreshStats stats = new RefreshStats();
        store.addListener(stats);
        StringMapView view = new StringMapView();
        store.addView(view);

        try (ScheduledRefresh refresh = new ScheduledRefresh(store, Duration.ofMillis(100), true)) {
            refresh.start();
            assertTrue(awaitSuccess(stats, 1, 3_000));
            assertEquals("v1", view.get().get("conf/a.txt"));

            Files.writeString(conf.resolve("a.txt"), "v2", StandardCharsets.UTF_8);
            long before = stats.successCount();
            assertTrue(awaitSuccess(stats, before + 1, 3_000));
            assertEquals("v2", view.get().get("conf/a.txt"));
        }
    }

    private static boolean awaitSuccess(RefreshStats stats, long minSuccess, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (stats.successCount() >= minSuccess) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
