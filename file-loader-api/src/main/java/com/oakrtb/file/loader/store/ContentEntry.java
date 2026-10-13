package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.spi.ContentMeta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One loaded file: heap {@code byte[]}, spilled local file, or meta-only (raw content discarded).
 */
public final class ContentEntry {

    private final ContentMeta meta;
    private final byte[] bytes;
    private final Path file;

    private ContentEntry(ContentMeta meta, byte[] bytes, Path file) {
        this.meta = Objects.requireNonNull(meta, "meta");
        if (bytes != null && file != null) {
            throw new IllegalArgumentException("at most one of bytes/file allowed");
        }
        this.bytes = bytes;
        this.file = file;
    }

    public static ContentEntry ofBytes(ContentMeta meta, byte[] bytes) {
        return new ContentEntry(meta, Objects.requireNonNull(bytes, "bytes"), null);
    }

    public static ContentEntry ofFile(ContentMeta meta, Path file) {
        return new ContentEntry(meta, null, Objects.requireNonNull(file, "file"));
    }

    /** Keeps version meta for incremental refresh; raw payload already discarded. */
    public static ContentEntry ofMeta(ContentMeta meta) {
        return new ContentEntry(meta, null, null);
    }

    public ContentMeta getMeta() {
        return meta;
    }

    public boolean hasContent() {
        return bytes != null || file != null;
    }

    public boolean isSpilled() {
        return file != null;
    }

    public Optional<Path> getFile() {
        return Optional.ofNullable(file);
    }

    public byte[] getBytes() {
        if (bytes != null) {
            return Arrays.copyOf(bytes, bytes.length);
        }
        if (file != null) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new IllegalStateException("failed to read spilled content: " + file, e);
            }
        }
        throw new IllegalStateException("content discarded; only meta is retained");
    }

    public InputStream openStream() throws IOException {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        if (file != null) {
            return Files.newInputStream(file);
        }
        throw new IOException("content discarded; only meta is retained");
    }

    public long length() {
        if (bytes != null) {
            return bytes.length;
        }
        if (file != null) {
            try {
                return Files.size(file);
            } catch (IOException e) {
                throw new IllegalStateException("failed to size spilled content: " + file, e);
            }
        }
        return meta.getSize();
    }
}
