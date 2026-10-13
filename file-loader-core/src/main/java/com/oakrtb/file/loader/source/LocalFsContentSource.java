package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local filesystem {@link ContentSource}. Paths are relative to {@code root}.
 */
public final class LocalFsContentSource implements ContentSource {

    private final Path root;

    public LocalFsContentSource(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Absolute path for a logical prefix under root (may not exist yet). */
    public Path resolvePrefix(String prefix) {
        return resolve(prefix == null ? "" : prefix);
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public List<ContentRef> list(String prefix) throws IOException {
        Path start = resolve(prefix);
        if (!Files.exists(start)) {
            return Collections.emptyList();
        }
        if (Files.isRegularFile(start)) {
            return Collections.singletonList(new ContentRef(toLogicalPath(start)));
        }
        try (Stream<Path> walk = Files.walk(start)) {
            return walk
                    .filter(Files::isRegularFile)
                    .map(this::toLogicalPath)
                    .map(ContentRef::new)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    @Override
    public Optional<ContentMeta> meta(ContentRef ref) throws IOException {
        Path path = resolve(ref.getPath());
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        Instant modified = attrs.lastModifiedTime().toInstant();
        long size = attrs.size();
        String etag = modified.toEpochMilli() + "-" + size;
        return Optional.of(new ContentMeta(etag, modified, size));
    }

    @Override
    public InputStream open(ContentRef ref) throws IOException {
        Path path = resolve(ref.getPath());
        if (!Files.isRegularFile(path)) {
            throw new IOException("not a file: " + ref.getPath());
        }
        return Files.newInputStream(path);
    }

    private Path resolve(String logicalPath) {
        String normalized = logicalPath == null ? "" : logicalPath;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes root: " + logicalPath);
        }
        return resolved;
    }

    private String toLogicalPath(Path absolute) {
        Path relative = root.relativize(absolute.normalize());
        return relative.toString().replace('\\', '/');
    }
}
