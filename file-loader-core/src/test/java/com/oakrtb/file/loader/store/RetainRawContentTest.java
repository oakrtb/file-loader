package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.view.JsonObjectView;
import com.oakrtb.file.loader.view.SourcedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainRawContentTest {

    @TempDir
    Path tempDir;

    public static final class App {
        public String name;
    }

    @Test
    void discardsRawBytesAfterViewsParseButKeepsBusinessObject() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("app.json"), "{\"name\":\"oak\"}", StandardCharsets.UTF_8);

        StoreOptions options = StoreOptions.builder().retainRawContent(false).build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        JsonObjectView<App> view = new JsonObjectView<>("conf/app.json", App.class);
        store.addView(view);

        assertTrue(store.refresh().isSuccess());
        ContentEntry entry = store.current().get("conf/app.json").orElseThrow();
        assertFalse(entry.hasContent());
        assertTrue(store.get("conf/app.json").isEmpty());

        Optional<SourcedValue<App>> loaded = view.get();
        assertTrue(loaded.isPresent());
        assertEquals("conf/app.json", loaded.get().path());
        assertEquals("oak", loaded.get().value().name);

        // unchanged file: meta reuse, view keeps previous
        assertTrue(store.refresh().isSuccess());
        assertFalse(store.current().get("conf/app.json").orElseThrow().hasContent());
        assertEquals("oak", view.get().orElseThrow().value().name);

        Files.writeString(conf.resolve("app.json"), "{\"name\":\"rtb\"}", StandardCharsets.UTF_8);
        assertTrue(store.refresh().isSuccess());
        assertFalse(store.current().get("conf/app.json").orElseThrow().hasContent());
        assertEquals("rtb", view.get().orElseThrow().value().name);
    }

    @Test
    void manifestRefreshWorksWhenRawContentDiscarded() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "A1", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v1",
                  "files": [ {"path": "conf/a.txt"} ]
                }
                """, StandardCharsets.UTF_8);

        StoreOptions options = StoreOptions.builder()
                .retainRawContent(false)
                .manifestEnabled(true)
                .build();
        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), "conf", null, options);
        assertTrue(store.refresh().isSuccess());
        assertEquals("v1", store.current().getManifestVersion().orElseThrow());
        assertFalse(store.current().get("conf/a.txt").orElseThrow().hasContent());

        // second refresh: meta-only reuse must not throw
        assertTrue(store.refresh().isSuccess());
        assertEquals("v1", store.current().getManifestVersion().orElseThrow());

        Files.writeString(conf.resolve("a.txt"), "A2", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v2",
                  "files": [ {"path": "conf/a.txt"} ]
                }
                """, StandardCharsets.UTF_8);
        assertTrue(store.refresh().isSuccess());
        assertEquals("v2", store.current().getManifestVersion().orElseThrow());
        assertFalse(store.current().get("conf/a.txt").orElseThrow().hasContent());
    }
}
