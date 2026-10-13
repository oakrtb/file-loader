package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.FileLoader;
import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFsWatchRefreshTest {

    @TempDir
    Path tempDir;

    @Test
    void watchesLocalFileChangeAndUpdatesView() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Path file = conf.resolve("a.txt");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .watchLocalChanges(true)
                .watchDebounce(Duration.ofMillis(100))
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            loader.start();
            assertEquals("v1", loader.<Map<String, String>>view("text").get().get("conf/a.txt"));

            long before = loader.stats().successCount();
            Files.writeString(file, "v2", StandardCharsets.UTF_8);
            assertTrue(await(() -> {
                String value = loader.<Map<String, String>>view("text").get().get("conf/a.txt");
                return "v2".equals(value) && loader.stats().successCount() > before;
            }, 5_000));
        }
    }

    private static boolean await(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }
}
