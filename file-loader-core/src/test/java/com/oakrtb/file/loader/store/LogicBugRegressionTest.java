package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for confirmed cache / strip / rolling-date / refresh-coalesce bugs.
 */
class LogicBugRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void cacheLoadThenRefreshDoesNotCorruptLargeCachedFile() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);

        byte[] big = new byte[256];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i & 0xff);
        }
        ContentMeta meta = new ContentMeta("e1", Instant.parse("2026-01-01T00:00:00Z"), big.length);
        cache.save(new ContentSnapshot(Instant.parse("2026-01-01T00:00:00Z"),
                java.util.Map.of("conf/big.bin", ContentEntry.ofBytes(meta, big))));

        // Force meta match so refresh REUSES ofFile pointing at the cache path.
        ContentSource source = fixedSource(List.of("conf/big.bin"), meta, big);
        StoreOptions options = StoreOptions.builder()
                .heapThresholdBytes(64)
                .spillDir(tempDir.resolve("spill-root"))
                .build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "conf", cache, options);
        store.loadFromCache();
        assertTrue(store.current().get("conf/big.bin").orElseThrow().isSpilled());

        assertTrue(store.refresh().isSuccess());

        Path cachedFile = cacheDir.resolve("files").resolve("conf").resolve("big.bin");
        assertTrue(Files.isRegularFile(cachedFile));
        assertArrayEquals(big, Files.readAllBytes(cachedFile));
        assertArrayEquals(big, store.current().get("conf/big.bin").orElseThrow().getBytes());
    }

    @Test
    void stripRawContentDoesNotDeleteCacheFiles() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);

        byte[] big = new byte[128];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        ContentMeta meta = new ContentMeta("e1", Instant.parse("2026-01-01T00:00:00Z"), big.length);
        cache.save(new ContentSnapshot(Instant.parse("2026-01-01T00:00:00Z"),
                java.util.Map.of("conf/big.bin", ContentEntry.ofBytes(meta, big))));

        ContentSource source = fixedSource(List.of("conf/big.bin"), meta, big);
        StoreOptions options = StoreOptions.builder()
                .heapThresholdBytes(32)
                .spillDir(tempDir.resolve("spill-root"))
                .retainRawContent(false)
                .build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "conf", cache, options);
        store.loadFromCache();
        assertTrue(store.refresh().isSuccess());

        Path cachedFile = cacheDir.resolve("files").resolve("conf").resolve("big.bin");
        assertTrue(Files.isRegularFile(cachedFile), "cache file must survive strip");
        assertArrayEquals(big, Files.readAllBytes(cachedFile));
        assertFalse(store.current().get("conf/big.bin").orElseThrow().hasContent());
    }

    @Test
    void retainRawFalseStillPersistsCacheOnSubsequentRefresh() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "v1", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("b.txt"), "keep", StandardCharsets.UTF_8);
        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);

        StoreOptions options = StoreOptions.builder().retainRawContent(false).build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", cache, options);

        assertTrue(store.refresh().isSuccess());
        assertEquals("v1", new String(cache.load().getBytes("conf/a.txt").orElseThrow(), StandardCharsets.UTF_8));
        assertEquals("keep", new String(cache.load().getBytes("conf/b.txt").orElseThrow(), StandardCharsets.UTF_8));

        // a changes (download), b unchanged (meta-only reuse) — save must still update a
        Files.writeString(conf.resolve("a.txt"), "v2", StandardCharsets.UTF_8);
        assertTrue(store.refresh().isSuccess());
        assertEquals("v2", new String(cache.load().getBytes("conf/a.txt").orElseThrow(), StandardCharsets.UTF_8));
        assertEquals("keep", new String(cache.load().getBytes("conf/b.txt").orElseThrow(), StandardCharsets.UTF_8));
    }

    private static ContentSource fixedSource(List<String> paths, ContentMeta meta, byte[] bytes) {
        return new ContentSource() {
            @Override
            public String name() {
                return "fixed";
            }

            @Override
            public List<ContentRef> list(String prefix) {
                return paths.stream().map(ContentRef::new).toList();
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.of(meta);
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Test
    void rollingDateKeepsPreviousWhenTodayFolderMissing() throws Exception {
        Path day = tempDir.resolve("task").resolve("2026-10-12");
        Files.createDirectories(day);
        Files.writeString(day.resolve("x.txt"), "day", StandardCharsets.UTF_8);

        ZoneId zone = ZoneId.of("UTC");
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-12T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId zoneId) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        PrefixResolver resolver = PrefixResolver.rollingDate(
                "task/", DateTimeFormatter.ofPattern("yyyy-MM-dd"), zone, clock);

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), resolver, null, StoreOptions.defaults());
        assertTrue(store.refresh().isSuccess());
        assertEquals(1, store.current().size());

        now.set(Instant.parse("2026-10-13T01:00:00Z"));
        assertTrue(store.refresh().isSuccess());
        assertEquals(1, store.current().size(), "must keep yesterday when today dir is empty/missing");
        assertEquals("day", new String(store.get("task/2026-10-12/x.txt").orElseThrow(), StandardCharsets.UTF_8));
    }

    @Test
    void fixedPrefixEmptyListStillClearsSnapshot() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "x", StandardCharsets.UTF_8);

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, StoreOptions.defaults());
        assertTrue(store.refresh().isSuccess());
        assertEquals(1, store.current().size());

        Files.delete(conf.resolve("a.txt"));
        assertTrue(store.refresh().isSuccess());
        assertEquals(0, store.current().size());
    }

    @Test
    void overlappingRefreshIsCoalescedIntoFollowUp() throws Exception {
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
                int n = listCalls.incrementAndGet();
                if (n == 1) {
                    started.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS)) {
                            throw new IOException("timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
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

        release.countDown();
        t.join(3_000);
        assertEquals(2, listCalls.get(), "pending skip must trigger one follow-up refresh");
    }

    @Test
    void overlappingRefreshDuringFailureStillFollowsUp() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger listCalls = new AtomicInteger();

        ContentSource blocking = new ContentSource() {
            @Override
            public String name() {
                return "blocking-fail";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                int n = listCalls.incrementAndGet();
                if (n == 1) {
                    started.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS)) {
                            throw new IOException("timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    throw new IOException("first-fail");
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
        Thread t = new Thread(() -> {
            try {
                store.refresh();
            } catch (Exception ignored) {
                // first attempt fails then follow-up may succeed
            }
        });
        t.start();
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertTrue(store.refresh().isSkipped());

        release.countDown();
        t.join(3_000);
        assertEquals(2, listCalls.get(), "pending skip must follow up even when first refresh throws");
    }
}
