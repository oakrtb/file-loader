package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.FileLoader;
import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCacheAndRefreshTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsSnapshotCache() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);

        ContentSnapshot snap = new ContentSnapshot(Instant.parse("2026-01-01T00:00:00Z"), java.util.Map.of(
                "conf/a.txt",
                ContentEntry.ofBytes(
                        new ContentMeta("e1", Instant.parse("2026-01-01T00:00:00Z"), 5),
                        "hello".getBytes(StandardCharsets.UTF_8))));
        cache.save(snap);

        ContentSnapshot loaded = cache.load();
        assertEquals(1, loaded.size());
        assertEquals("hello", new String(loaded.getBytes("conf/a.txt").orElseThrow(), StandardCharsets.UTF_8));
        assertEquals("e1", loaded.get("conf/a.txt").orElseThrow().getMeta().getEtag());
    }

    @Test
    void overlappingRefreshIsSkipped() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger listCalls = new AtomicInteger();

        ContentSource blocking = new ContentSource() {
            @Override
            public String name() {
                return "blocking";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                listCalls.incrementAndGet();
                started.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                return List.of();
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.empty();
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream(new byte[0]);
            }
        };

        DefaultInMemoryStore store = new DefaultInMemoryStore(blocking, "");
        RefreshStats stats = new RefreshStats();
        store.addListener(stats);

        Thread t = new Thread(() -> {
            try {
                store.refresh();
            } catch (Exception ignored) {
                // ignore
            }
        });
        t.start();
        assertTrue(started.await(3, TimeUnit.SECONDS));

        RefreshResult skipped = store.refresh();
        assertTrue(skipped.isSkipped());
        assertEquals(1, stats.skippedCount());

        release.countDown();
        t.join(3_000);
        // Overlapping call marks pending; in-flight refresh coalesces one follow-up.
        assertEquals(2, listCalls.get());
    }

    @Test
    void startFallsBackToCacheWhenSourceFails() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "cached", StandardCharsets.UTF_8);
        Path cacheDir = tempDir.resolve("cache");

        try (FileLoader warmer = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .cacheDir(cacheDir)
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            warmer.start();
            assertEquals("cached", warmer.<java.util.Map<String, String>>view("text").get().get("conf/a.txt"));
        }

        ContentSource failing = new ContentSource() {
            @Override
            public String name() {
                return "fail";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.empty();
            }

            @Override
            public InputStream open(ContentRef ref) {
                return InputStream.nullInputStream();
            }
        };

        try (FileLoader loader = FileLoader.builder()
                .source(failing, false)
                .prefix("conf")
                .cacheDir(cacheDir)
                .cacheFallback(true)
                .failFast(true)
                .loggingListener(false)
                .addView("text", new StringMapView())
                .build()) {
            loader.start();
            assertEquals("cached", loader.<java.util.Map<String, String>>view("text").get().get("conf/a.txt"));
            assertTrue(loader.stats().failureCount() >= 1);
        }
    }
}
