package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.view.BytesMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultInMemoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshLoadsLocalFilesIntoMemoryAndView() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.write(conf.resolve("a.txt"), "hello".getBytes(StandardCharsets.UTF_8));

        LocalFsContentSource source = new LocalFsContentSource(tempDir);
        DefaultInMemoryStore store = new DefaultInMemoryStore(source, "conf");
        BytesMapView view = new BytesMapView();
        store.addView(view);

        store.refresh();

        assertTrue(store.get("conf/a.txt").isPresent());
        assertEquals("hello", new String(store.get("conf/a.txt").get(), StandardCharsets.UTF_8));

        Map<String, byte[]> bytes = view.get();
        assertEquals(1, bytes.size());
        assertEquals("hello", new String(bytes.get("conf/a.txt"), StandardCharsets.UTF_8));
    }
}
