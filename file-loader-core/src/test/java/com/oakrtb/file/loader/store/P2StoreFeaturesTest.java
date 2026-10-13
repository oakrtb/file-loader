package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P2StoreFeaturesTest {

    @TempDir
    Path tempDir;

    @Test
    void spillsLargeFilesToDisk() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        byte[] big = new byte[2048];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) ('a' + (i % 26));
        }
        Files.write(conf.resolve("big.bin"), big);

        Path spill = tempDir.resolve("spill");
        StoreOptions options = StoreOptions.builder()
                .heapThresholdBytes(512)
                .spillDir(spill)
                .downloadConcurrency(2)
                .build();

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        RefreshResult result = store.refresh();
        assertTrue(result.isSuccess());

        ContentEntry entry = store.current().get("conf/big.bin").orElseThrow();
        assertTrue(entry.isSpilled());
        assertEquals(big.length, entry.length());
        assertEquals(new String(big, StandardCharsets.ISO_8859_1),
                new String(entry.getBytes(), StandardCharsets.ISO_8859_1));
    }

    @Test
    void manifestSwitchesAtomicallyAndKeepsOldOnIncomplete() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "A1", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("b.txt"), "B1", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v1",
                  "files": [
                    {"path": "conf/a.txt"},
                    {"path": "conf/b.txt"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        StoreOptions options = StoreOptions.builder()
                .manifestEnabled(true)
                .downloadConcurrency(2)
                .build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        StringMapView view = new StringMapView();
        store.addView(view);

        assertTrue(store.refresh().isSuccess());
        assertEquals("v1", store.current().getManifestVersion().orElseThrow());
        assertEquals("A1", view.get().get("conf/a.txt"));
        assertEquals("B1", view.get().get("conf/b.txt"));

        Files.writeString(conf.resolve("a.txt"), "A2", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v2",
                  "files": [
                    {"path": "conf/a.txt"},
                    {"path": "conf/missing.txt"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        RefreshResult failed = store.refresh();
        assertFalse(failed.isSuccess());
        assertEquals("v1", store.current().getManifestVersion().orElseThrow());
        assertEquals("A1", view.get().get("conf/a.txt"));
        assertEquals("B1", view.get().get("conf/b.txt"));

        Files.writeString(conf.resolve("missing.txt"), "M2", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v2",
                  "files": [
                    {"path": "conf/a.txt"},
                    {"path": "conf/missing.txt"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        assertTrue(store.refresh().isSuccess());
        assertEquals("v2", store.current().getManifestVersion().orElseThrow());
        assertEquals("A2", view.get().get("conf/a.txt"));
        assertEquals("M2", view.get().get("conf/missing.txt"));
        assertFalse(view.get().containsKey("conf/b.txt"));
    }

    @Test
    void parallelDownloadLoadsMultipleFiles() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        for (int i = 0; i < 8; i++) {
            Files.writeString(conf.resolve("f" + i + ".txt"), "v" + i, StandardCharsets.UTF_8);
        }
        StoreOptions options = StoreOptions.builder().downloadConcurrency(4).build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        RefreshResult result = store.refresh();
        assertTrue(result.isSuccess());
        assertEquals(8, store.current().size());
        assertEquals(8, result.getDownloaded());
    }
}
