package com.oakrtb.file.loader.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLoaderConfigTest {

    @Test
    void parsesLocalConfigFromProperties() {
        Properties props = new Properties();
        props.setProperty("FILE_LOADER_SOURCE", "local");
        props.setProperty("FILE_LOADER_LOCAL_ROOT", "/tmp/data");
        props.setProperty("FILE_LOADER_PREFIX", "conf/");
        props.setProperty("FILE_LOADER_REFRESH_INTERVAL", "30s");
        props.setProperty("FILE_LOADER_CACHE_DIR", "/tmp/cache");
        props.setProperty("FILE_LOADER_CACHE_FALLBACK", "true");
        props.setProperty("FILE_LOADER_FAIL_FAST", "false");

        FileLoaderConfig config = FileLoaderConfig.fromProperties(props);
        assertEquals(FileLoaderConfig.SourceType.LOCAL, config.sourceType());
        assertEquals("/tmp/data", config.localRoot().orElseThrow().toString());
        assertEquals("conf/", config.prefixResolver().resolve());
        assertEquals(Duration.ofSeconds(30), config.refreshInterval().orElseThrow());
        assertTrue(config.cacheFallback());
        assertFalse(config.failFast());
    }

    @Test
    void parsesOssRequiresCredentials() {
        Properties props = new Properties();
        props.setProperty("FILE_LOADER_SOURCE", "oss");
        props.setProperty("FILE_LOADER_ENDPOINT", "https://oss.example");
        props.setProperty("FILE_LOADER_ACCESS_KEY", "ak");
        props.setProperty("FILE_LOADER_ACCESS_SECRET", "sk");
        props.setProperty("FILE_LOADER_BUCKET", "bkt");

        FileLoaderConfig config = FileLoaderConfig.fromProperties(props);
        assertEquals(FileLoaderConfig.SourceType.OSS, config.sourceType());
        assertEquals("bkt", config.objectStore().orElseThrow().bucket());
    }

    @Test
    void tosMissingRegionFails() {
        Properties props = new Properties();
        props.setProperty("FILE_LOADER_SOURCE", "tos");
        props.setProperty("FILE_LOADER_ENDPOINT", "https://tos.example");
        props.setProperty("FILE_LOADER_ACCESS_KEY", "ak");
        props.setProperty("FILE_LOADER_ACCESS_SECRET", "sk");
        props.setProperty("FILE_LOADER_BUCKET", "bkt");

        assertThrows(IllegalArgumentException.class, () -> FileLoaderConfig.fromProperties(props));
    }

    @Test
    void parsesRollingDatePrefixAndWatchPollFallback() {
        Properties props = new Properties();
        props.setProperty("FILE_LOADER_SOURCE", "local");
        props.setProperty("FILE_LOADER_LOCAL_ROOT", "/tmp/data");
        props.setProperty("FILE_LOADER_ROLLING_DATE_PREFIX", "task/");
        props.setProperty("FILE_LOADER_DATE_PATTERN", "yyyyMMdd");
        props.setProperty("FILE_LOADER_WATCH_POLL_FALLBACK", "false");

        FileLoaderConfig config = FileLoaderConfig.fromProperties(props);
        assertEquals("task/", config.watchRootPrefix());
        assertTrue(config.prefixResolver().resolve().startsWith("task/"));
        assertTrue(config.prefixResolver().resolve().endsWith("/"));
        assertFalse(config.watchPollFallback());
    }
}
