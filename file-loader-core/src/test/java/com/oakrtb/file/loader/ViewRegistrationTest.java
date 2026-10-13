package com.oakrtb.file.loader;

import com.oakrtb.file.loader.view.JsonObjectView;
import com.oakrtb.file.loader.view.SourcedValue;
import com.oakrtb.file.loader.view.StringMapView;
import com.oakrtb.file.loader.view.ViewRegistration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRegistrationTest {

    @TempDir
    Path tempDir;

    public static final class App {
        public String name;
    }

    @Test
    void builderAcceptsViewRegistrations() throws Exception {
        Path conf = tempDir.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("app.json"), "{\"name\":\"oak\"}", StandardCharsets.UTF_8);
        Files.writeString(conf.resolve("notes.txt"), "hi", StandardCharsets.UTF_8);

        try (FileLoader loader = FileLoader.builder()
                .local(tempDir)
                .prefix("conf")
                .loggingListener(false)
                .addView(ViewRegistration.of("text", new StringMapView()))
                .addViews(List.of(
                        ViewRegistration.of("app", new JsonObjectView<>("conf/app.json", App.class))))
                .build()) {
            loader.start();
            Map<String, String> text = loader.<Map<String, String>>view("text").get();
            assertEquals("hi", text.get("conf/notes.txt"));
            Optional<SourcedValue<App>> app = loader.<Optional<SourcedValue<App>>>view("app").get();
            assertTrue(app.isPresent());
            assertEquals("oak", app.get().value().name);
        }
    }
}
