package com.oakrtb.file.loader;

import com.oakrtb.file.loader.store.StoreOptions;
import com.oakrtb.file.loader.view.StringMapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLoaderFailFastTest {

    @TempDir
    Path tempDir;

    @Test
    void startFailsFastWhenManifestIncomplete() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.txt"), "A", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("manifest.json"), """
                {
                  "version": "v1",
                  "files": [
                    {"path": "conf/a.txt"},
                    {"path": "conf/missing.txt"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .failFast(true)
                .loggingListener(false)
                .storeOptions(StoreOptions.builder().manifestEnabled(true).build())
                .addView("text", new StringMapView())
                .build()) {
            Exception ex = assertThrows(Exception.class, loader::start);
            assertTrue(ex.getMessage().contains("manifest") || ex.getCause() != null
                    || ex.getMessage().contains("incomplete"));
        }
    }
}
