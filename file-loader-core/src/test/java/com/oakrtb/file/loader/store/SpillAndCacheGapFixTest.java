package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.spi.ContentMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpillAndCacheGapFixTest {

    @TempDir
    Path tempDir;

    @Test
    void cleansOrphanSpillFilesAfterRefresh() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "A", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("b.txt"), "BBBBBBBB", StandardCharsets.UTF_8);

        Path spill = tempDir.resolve("spill-root");
        StoreOptions options = StoreOptions.builder()
                .heapThresholdBytes(2)
                .spillDir(spill)
                .build();

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        assertTrue(store.refresh().isSuccess());
        assertTrue(store.current().get("conf/b.txt").orElseThrow().isSpilled());
        Path liveSpill = store.current().get("conf/b.txt").orElseThrow().getFile().orElseThrow();
        assertTrue(Files.isRegularFile(liveSpill));

        Path orphan = spill.resolve("spill").resolve("orphan-old");
        Files.writeString(orphan, "stale", StandardCharsets.UTF_8);
        assertTrue(Files.isRegularFile(orphan));

        Files.delete(conf.resolve("b.txt"));
        assertTrue(store.refresh().isSuccess());
        assertFalse(Files.exists(orphan));
        assertFalse(Files.exists(liveSpill));
        assertFalse(store.current().get("conf/b.txt").isPresent());
    }

    @Test
    void cacheLoadKeepsLargeFilesOnDisk() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);

        byte[] big = new byte[1024];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        ContentSnapshot snap = new ContentSnapshot(Instant.parse("2026-01-01T00:00:00Z"), Map.of(
                "conf/big.bin",
                ContentEntry.ofBytes(new ContentMeta("e1", Instant.parse("2026-01-01T00:00:00Z"), big.length), big),
                "conf/small.txt",
                ContentEntry.ofBytes(
                        new ContentMeta("e2", Instant.parse("2026-01-01T00:00:00Z"), 5),
                        "hello".getBytes(StandardCharsets.UTF_8))));
        cache.save(snap);

        StoreOptions options = StoreOptions.builder().heapThresholdBytes(100).build();
        ContentSnapshot loaded = cache.load(options);

        ContentEntry bigEntry = loaded.get("conf/big.bin").orElseThrow();
        ContentEntry smallEntry = loaded.get("conf/small.txt").orElseThrow();
        assertTrue(bigEntry.isSpilled());
        assertFalse(smallEntry.isSpilled());
        assertEquals(big.length, bigEntry.getBytes().length);
        assertEquals("hello", new String(smallEntry.getBytes(), StandardCharsets.UTF_8));
    }
}
