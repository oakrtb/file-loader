package com.oakrtb.file.loader.store;

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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowLevelLogicFixTest {

    @TempDir
    Path tempDir;

    @Test
    void pathUnderPrefixRespectsDirectoryBoundary() {
        assertTrue(DefaultInMemoryStore.pathUnderPrefix("conf/a.txt", "conf"));
        assertTrue(DefaultInMemoryStore.pathUnderPrefix("conf/a.txt", "conf/"));
        assertTrue(DefaultInMemoryStore.pathUnderPrefix("conf", "conf"));
        assertFalse(DefaultInMemoryStore.pathUnderPrefix("config/a.txt", "conf"));
        assertFalse(DefaultInMemoryStore.pathUnderPrefix("config/a.txt", "conf/"));
    }

    @Test
    void emptyListDoesNotClearWhenPrefixIsStringPrefixOfUnrelatedPaths() throws Exception {
        // previous under "config/", resolver now returns "conf" with empty list —
        // must NOT treat config/* as under conf and wipe.
        Map<String, byte[]> files = new HashMap<>();
        ContentSource source = new ContentSource() {
            @Override
            public String name() {
                return "mem";
            }

            @Override
            public List<ContentRef> list(String prefix) {
                if ("config/".equals(prefix) || "config".equals(prefix)) {
                    return List.of(new ContentRef("config/a.txt"));
                }
                return List.of();
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                byte[] bytes = files.get(ref.getPath());
                if (bytes == null) {
                    return Optional.empty();
                }
                return Optional.of(new ContentMeta("e", Instant.EPOCH, bytes.length));
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream(files.get(ref.getPath()));
            }
        };
        files.put("config/a.txt", "x".getBytes(StandardCharsets.UTF_8));

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                source, PrefixResolver.fixed("config/"), null, StoreOptions.defaults());
        assertTrue(store.refresh().isSuccess());
        assertEquals(1, store.current().size());

        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);
        cache.save(store.current());

        DefaultInMemoryStore confStore = new DefaultInMemoryStore(
                source, PrefixResolver.fixed("conf"), cache, StoreOptions.defaults());
        confStore.loadFromCache();
        assertEquals(1, confStore.current().size());
        assertTrue(confStore.refresh().isSuccess());
        assertEquals(1, confStore.current().size(), "conf must not match config/* via startsWith");
        assertTrue(confStore.current().get("config/a.txt").isPresent());
    }

    @Test
    void failedFilesIncludesHardFailures() throws Exception {
        ContentSource source = new ContentSource() {
            @Override
            public String name() {
                return "partial";
            }

            @Override
            public List<ContentRef> list(String prefix) {
                return List.of(new ContentRef("a.txt"), new ContentRef("b.txt"));
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) throws IOException {
                if ("b.txt".equals(ref.getPath())) {
                    throw new IOException("meta boom");
                }
                return Optional.of(new ContentMeta("e1", Instant.EPOCH, 1));
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8));
            }
        };

        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "", null, StoreOptions.defaults());
        RefreshResult result = store.refresh();
        assertTrue(result.isSuccess());
        assertEquals(1, result.getEntryCount());
        assertEquals(1, result.getFailedFiles(), "hard failure on new file must count");
    }

    @Test
    void manifestMetaOnlyReuseStillRepairsIncompleteSnapshot() throws Exception {
        byte[] manifestBytes = """
                {
                  "version": "v1",
                  "files": [
                    {"path": "conf/a.txt"},
                    {"path": "conf/b.txt"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        ContentMeta manifestMeta = new ContentMeta("m1", Instant.EPOCH, manifestBytes.length);
        ContentMeta aMeta = new ContentMeta("a1", Instant.EPOCH, 1);
        ContentMeta bMeta = new ContentMeta("b1", Instant.EPOCH, 1);
        Map<String, byte[]> body = Map.of(
                "conf/manifest.json", manifestBytes,
                "conf/a.txt", "A".getBytes(StandardCharsets.UTF_8),
                "conf/b.txt", "B".getBytes(StandardCharsets.UTF_8));
        Map<String, ContentMeta> metas = Map.of(
                "conf/manifest.json", manifestMeta,
                "conf/a.txt", aMeta,
                "conf/b.txt", bMeta);

        ContentSource source = new ContentSource() {
            @Override
            public String name() {
                return "fixed-manifest";
            }

            @Override
            public List<ContentRef> list(String prefix) {
                return List.of();
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.ofNullable(metas.get(ref.getPath()));
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream(body.get(ref.getPath()));
            }
        };

        Path cacheDir = tempDir.resolve("cache");
        SnapshotCache cache = new SnapshotCache(cacheDir);
        // Incomplete snapshot: same manifest version/meta, but missing conf/b.txt
        cache.save(new ContentSnapshot(Instant.EPOCH, Map.of(
                "conf/manifest.json", ContentEntry.ofBytes(manifestMeta, manifestBytes),
                "conf/a.txt", ContentEntry.ofBytes(aMeta, "A".getBytes(StandardCharsets.UTF_8))),
                "v1"));

        StoreOptions options = StoreOptions.builder()
                .retainRawContent(false)
                .manifestEnabled(true)
                .build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "conf", cache, options);
        store.loadFromCache();
        assertFalse(store.current().get("conf/b.txt").isPresent());

        // First refresh strips to meta-only; second hits meta-only reuse path and must repair.
        assertTrue(store.refresh().isSuccess());
        assertTrue(store.current().get("conf/b.txt").isPresent());
        assertEquals(3, store.current().size());

        assertTrue(store.refresh().isSuccess());
        assertTrue(store.current().get("conf/b.txt").isPresent());
        assertEquals(3, store.current().size());
    }
}
