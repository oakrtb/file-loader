package com.oakrtb.file.loader;

import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLoaderWatchPollFallbackTest {

    @TempDir
    Path tempDir;

    @Test
    void watchWithoutRefreshEveryEnablesDefaultPollFallback() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "v1", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .watchLocalChanges(true)
                .watchPollFallback(true)
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            assertTrue(loader.watchEnabled());
            assertEquals(FileLoader.DEFAULT_WATCH_POLL_FALLBACK, loader.scheduledRefreshInterval().orElseThrow());
            loader.start();
        }
    }

    @Test
    void watchPollFallbackCanBeDisabled() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "v1", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .watchLocalChanges(true)
                .watchPollFallback(false)
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            assertTrue(loader.watchEnabled());
            assertTrue(loader.scheduledRefreshInterval().isEmpty());
        }
    }

    @Test
    void explicitRefreshEveryWinsOverDefaultFallback() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "v1", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .watchLocalChanges(true)
                .refreshEvery(Duration.ofSeconds(15))
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            assertEquals(Duration.ofSeconds(15), loader.scheduledRefreshInterval().orElseThrow());
        }
    }
}
