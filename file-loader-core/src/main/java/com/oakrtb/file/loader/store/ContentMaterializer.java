package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.spi.ContentMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Loads an {@link InputStream} into heap or spills to disk when over threshold.
 */
public final class ContentMaterializer {

    private final StoreOptions options;

    public ContentMaterializer(StoreOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public ContentEntry materialize(String logicalPath, ContentMeta meta, InputStream in) throws IOException {
        Objects.requireNonNull(logicalPath, "logicalPath");
        Objects.requireNonNull(meta, "meta");
        long threshold = options.heapThresholdBytes();
        Path spillDir = options.spillDir();

        if (spillDir != null && meta.getSize() > threshold) {
            return spillToFile(logicalPath, meta, in);
        }

        try (InputStream stream = in) {
            if (spillDir == null || threshold == Long.MAX_VALUE) {
                return ContentEntry.ofBytes(meta, readAll(stream));
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) >= 0) {
                buffer.write(buf, 0, n);
                if (buffer.size() > threshold) {
                    Path file = newSpillFile(logicalPath);
                    Files.createDirectories(file.getParent());
                    try (OutputStream out = Files.newOutputStream(file)) {
                        buffer.writeTo(out);
                        stream.transferTo(out);
                    }
                    return ContentEntry.ofFile(meta, file);
                }
            }
            return ContentEntry.ofBytes(meta, buffer.toByteArray());
        }
    }

    private ContentEntry spillToFile(String logicalPath, ContentMeta meta, InputStream in) throws IOException {
        Path file = newSpillFile(logicalPath);
        Files.createDirectories(file.getParent());
        try (InputStream stream = in; OutputStream out = Files.newOutputStream(file)) {
            stream.transferTo(out);
        }
        return ContentEntry.ofFile(meta, file);
    }

    private Path newSpillFile(String logicalPath) {
        Path base = options.spillDir().toAbsolutePath().normalize();
        String safe = Integer.toHexString(logicalPath.hashCode()) + "-" + UUID.randomUUID();
        return base.resolve("spill").resolve(safe);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }
}
