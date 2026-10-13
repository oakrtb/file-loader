package com.oakrtb.file.loader.store;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable in-memory snapshot of loaded file resources.
 */
public final class ContentSnapshot {

    private final Instant loadedAt;
    private final Map<String, ContentEntry> entries;
    private final String manifestVersion;

    public ContentSnapshot(Instant loadedAt, Map<String, ContentEntry> entries) {
        this(loadedAt, entries, null);
    }

    public ContentSnapshot(Instant loadedAt, Map<String, ContentEntry> entries, String manifestVersion) {
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(entries, "entries")));
        this.manifestVersion = manifestVersion;
    }

    public static ContentSnapshot empty() {
        return new ContentSnapshot(Instant.EPOCH, Map.of(), null);
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public Map<String, ContentEntry> getEntries() {
        return entries;
    }

    public Optional<String> getManifestVersion() {
        return Optional.ofNullable(manifestVersion);
    }

    public Optional<ContentEntry> get(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    public Optional<byte[]> getBytes(String path) {
        ContentEntry entry = entries.get(path);
        if (entry == null || !entry.hasContent()) {
            return Optional.empty();
        }
        return Optional.of(entry.getBytes());
    }

    public int size() {
        return entries.size();
    }
}
