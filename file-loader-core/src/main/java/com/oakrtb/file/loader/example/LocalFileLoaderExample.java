package com.oakrtb.file.loader.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.oakrtb.file.loader.FileLoader;
import com.oakrtb.file.loader.view.JsonNodeMapView;
import com.oakrtb.file.loader.view.JsonObjectView;
import com.oakrtb.file.loader.view.PropertiesMapView;
import com.oakrtb.file.loader.view.SourcedValue;
import com.oakrtb.file.loader.view.StringMapView;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Local demo:
 * <pre>
 *   conf/
 *     app.json
 *     feature.properties
 *     notes.txt
 * </pre>
 */
public final class LocalFileLoaderExample {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        try (FileLoader loader = FileLoader.builder()
                .local(root)
                .prefix("conf")
                .refreshEvery(Duration.ofSeconds(30))
                .cacheDir(root.resolve(".file-loader-cache"))
                .cacheFallback(true)
                .addView("json", new JsonNodeMapView())
                .addView("props", new PropertiesMapView())
                .addView("text", new StringMapView())
                .addView("app", new JsonObjectView<>("conf/app.json", AppConfig.class))
                .build()) {

            loader.start(); // fail-fast first load; fallback to cache if enabled

            Map<String, JsonNode> json = loader.<Map<String, JsonNode>>view("json").get();
            Map<String, Properties> props = loader.<Map<String, Properties>>view("props").get();
            Optional<SourcedValue<AppConfig>> app = loader.<Optional<SourcedValue<AppConfig>>>view("app").get();

            System.out.println("json files=" + json.keySet());
            System.out.println("props files=" + props.keySet());
            System.out.println("app path=" + app.map(SourcedValue::path).orElse(null)
                    + " value=" + app.map(SourcedValue::value).orElse(null));
            System.out.println("stats success=" + loader.stats().successCount()
                    + " failure=" + loader.stats().failureCount());
        }
    }

    public static final class AppConfig {
        public String name;
        public int version;

        @Override
        public String toString() {
            return "AppConfig{name='" + name + "', version=" + version + '}';
        }
    }

    private LocalFileLoaderExample() {
    }
}
