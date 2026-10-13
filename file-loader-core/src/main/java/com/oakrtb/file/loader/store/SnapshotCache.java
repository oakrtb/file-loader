package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.spi.ContentMeta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Persists a {@link ContentSnapshot} under a local directory for startup fallback.
 *
 * <pre>
 * cacheDir/
 *   meta.properties          # loadedAt + path metadata
 *   files/&lt;logical-path&gt;     # raw bytes
 * </pre>
 */
public final class SnapshotCache {

    private static final String META_FILE = "meta.properties";
    private static final String FILES_DIR = "files";

    private final Path root;

    public SnapshotCache(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public void save(ContentSnapshot snapshot) throws IOException {
        Path files = root.resolve(FILES_DIR);
        Files.createDirectories(files);

        Properties meta = new Properties();
        meta.setProperty("loadedAt", snapshot.getLoadedAt().toString());
        meta.setProperty("entryCount", String.valueOf(snapshot.size()));
        if (snapshot.getManifestVersion().isPresent()) {
            meta.setProperty("manifestVersion", snapshot.getManifestVersion().get());
        }

        for (Map.Entry<String, ContentEntry> e : snapshot.getEntries().entrySet()) {
            String path = e.getKey();
            ContentEntry entry = e.getValue();
            Path target = resolveFile(path);
            Files.createDirectories(target.getParent());
            if (!writeEntryContent(entry, target)) {
                continue;
            }

            ContentMeta m = entry.getMeta();
            String key = encodeKey(path);
            meta.setProperty(key + ".etag", nullToEmpty(m.getEtag()));
            meta.setProperty(key + ".lastModified", m.getLastModified() == null ? "" : m.getLastModified().toString());
            meta.setProperty(key + ".size", String.valueOf(m.getSize()));
            meta.setProperty(key + ".path", path);
        }

        Path metaPath = root.resolve(META_FILE);
        Path metaTmp = root.resolve(META_FILE + ".tmp");
        try (OutputStream out = Files.newOutputStream(metaTmp)) {
            meta.store(out, "file-loader snapshot cache");
        }
        try {
            Files.move(metaTmp, metaPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(metaTmp, metaPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Writes entry bytes to {@code target}. Safe when the entry already points at {@code target}
     * (e.g. large-file load-from-cache reuse): skips in-place truncate. Meta-only entries leave
     * an existing cache file untouched so {@code retainRawContent=false} refreshes can still
     * persist newly downloaded siblings.
     *
     * @return {@code false} when the entry cannot be represented in cache (meta-only, no file)
     */
    private static boolean writeEntryContent(ContentEntry entry, Path target) throws IOException {
        if (!entry.hasContent()) {
            return Files.isRegularFile(target);
        }
        if (entry.getFile().isPresent()) {
            Path source = entry.getFile().get().toAbsolutePath().normalize();
            if (source.equals(target.toAbsolutePath().normalize())) {
                return true;
            }
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = entry.openStream(); OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    public ContentSnapshot load() throws IOException {
        return load(StoreOptions.defaults());
    }

    /**
     * Loads cache entries. Files larger than {@link StoreOptions#heapThresholdBytes()} stay on disk
     * ({@link ContentEntry#ofFile}) instead of being fully read into the heap.
     */
    public ContentSnapshot load(StoreOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        Path metaPath = root.resolve(META_FILE);
        if (!Files.isRegularFile(metaPath)) {
            return ContentSnapshot.empty();
        }
        Properties meta = new Properties();
        try (InputStream in = Files.newInputStream(metaPath)) {
            meta.load(in);
        }

        Instant loadedAt = Instant.parse(meta.getProperty("loadedAt", Instant.EPOCH.toString()));
        String manifestVersion = emptyToNull(meta.getProperty("manifestVersion"));
        Map<String, ContentEntry> entries = new LinkedHashMap<>();
        long threshold = options.heapThresholdBytes();

        for (String name : meta.stringPropertyNames()) {
            if (!name.endsWith(".path")) {
                continue;
            }
            String key = name.substring(0, name.length() - ".path".length());
            String path = meta.getProperty(name);
            Path file = resolveFile(path);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String etag = emptyToNull(meta.getProperty(key + ".etag"));
            String lm = meta.getProperty(key + ".lastModified", "");
            Instant modified = lm.isBlank() ? null : Instant.parse(lm);
            long size = Long.parseLong(meta.getProperty(key + ".size", String.valueOf(Files.size(file))));
            ContentMeta contentMeta = new ContentMeta(etag, modified, size);
            if (size > threshold) {
                entries.put(path, ContentEntry.ofFile(contentMeta, file));
            } else {
                entries.put(path, ContentEntry.ofBytes(contentMeta, Files.readAllBytes(file)));
            }
        }
        return new ContentSnapshot(loadedAt, entries, manifestVersion);
    }

    public boolean exists() {
        return Files.isRegularFile(root.resolve(META_FILE));
    }

    /** True when {@code file} lives under this cache's {@code files/} tree. */
    public boolean isCachedFile(Path file) {
        if (file == null) {
            return false;
        }
        Path filesRoot = root.resolve(FILES_DIR).toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(filesRoot);
    }

    private Path resolveFile(String logicalPath) {
        String normalized = logicalPath == null ? "" : logicalPath;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = root.resolve(FILES_DIR).resolve(normalized).normalize();
        if (!resolved.startsWith(root.resolve(FILES_DIR))) {
            throw new IllegalArgumentException("path escapes cache root: " + logicalPath);
        }
        return resolved;
    }

    private static String encodeKey(String path) {
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("e.");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
