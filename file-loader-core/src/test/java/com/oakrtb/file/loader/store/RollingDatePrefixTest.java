package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingDatePrefixTest {

    @TempDir
    Path tempDir;

    @Test
    void storeLoadsOnlyRollingDatePrefix() throws Exception {
        Path day = tempDir.resolve("task").resolve("2026-10-12");
        Files.createDirectories(day);
        Files.writeString(day.resolve("x.txt"), "day", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("task").resolve("2026-10-11"));
        Files.writeString(tempDir.resolve("task").resolve("2026-10-11").resolve("old.txt"), "old", StandardCharsets.UTF_8);

        ZoneId zone = ZoneId.of("UTC");
        PrefixResolver resolver = PrefixResolver.rollingDate(
                "task/",
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                zone,
                Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), zone));

        DefaultInMemoryStore store = new DefaultInMemoryStore(
                new LocalFsContentSource(tempDir), resolver, null, StoreOptions.defaults());
        assertTrue(store.refresh().isSuccess());
        assertEquals(1, store.current().size());
        assertEquals("day", new String(store.get("task/2026-10-12/x.txt").orElseThrow(), StandardCharsets.UTF_8));
    }
}
