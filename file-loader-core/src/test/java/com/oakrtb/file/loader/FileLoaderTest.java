package com.oakrtb.file.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.oakrtb.file.loader.view.JsonNodeMapView;
import com.oakrtb.file.loader.view.JsonObjectView;
import com.oakrtb.file.loader.view.PropertiesMapView;
import com.oakrtb.file.loader.view.SourcedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void startLoadsJsonPropertiesAndTypedObject() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("app.json"), "{\"name\":\"oak\",\"version\":1}", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("feature.properties"), "enabled=true\n", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("notes.txt"), "hello", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .refreshEvery(Duration.ofHours(1))
                .addView("json", new JsonNodeMapView())
                .addView("props", new PropertiesMapView())
                .addView("app", new JsonObjectView<>("conf/app.json", AppConfig.class))
                .build()) {

            loader.start();

            Map<String, JsonNode> json = loader.<Map<String, JsonNode>>view("json").get();
            assertEquals(1, json.size());
            assertEquals("oak", json.get("conf/app.json").get("name").asText());

            Map<String, Properties> props = loader.<Map<String, Properties>>view("props").get();
            assertEquals("true", props.get("conf/feature.properties").getProperty("enabled"));

            Optional<SourcedValue<AppConfig>> app = loader.<Optional<SourcedValue<AppConfig>>>view("app").get();
            assertTrue(app.isPresent());
            assertEquals("conf/app.json", app.get().path());
            assertEquals("oak", app.get().value().name);
            assertEquals(1, app.get().value().version);
        }
    }

    @Test
    void jsonParseFailureKeepsPreviousValue() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Path app = conf.resolve("app.json");
        Files.writeString(app, "{\"name\":\"ok\"}", StandardCharsets.UTF_8);

        JsonNodeMapView view = new JsonNodeMapView();
        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .addView("json", view)
                .build()) {
            loader.start();
            assertEquals("ok", view.get().get("conf/app.json").get("name").asText());

            Files.writeString(app, "{broken", StandardCharsets.UTF_8);
            loader.store().refresh();
            assertEquals("ok", view.get().get("conf/app.json").get("name").asText());
        }
    }

    @Test
    void startFailsWhenPrefixMissingOnEmptyRootStillOkButUnknownViewThrows() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("a.json"), "{}", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .addView("json", new JsonNodeMapView())
                .build()) {
            loader.start();
            assertThrows(IllegalArgumentException.class, () -> loader.view("missing"));
        }
    }

    public static final class AppConfig {
        public String name;
        public int version;
    }
}
