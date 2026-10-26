package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.spi.ObjectStoreSourceFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test SPI provider. Sees the config {@link ObjectStoreSources} hands the SDK factory.
 */
public final class RecordingObjectStoreSourceFactory implements ObjectStoreSourceFactory {

    static final AtomicReference<ObjectStoreConfig> received = new AtomicReference<>();
    static final AtomicInteger listCalls = new AtomicInteger();
    /** First successful {@code list} attempt (1-based). Earlier attempts throw. */
    static volatile int succeedOnAttempt = 1;

    @Override
    public String type() {
        return "recording";
    }

    @Override
    public ContentSource create(ObjectStoreConfig config) {
        received.set(config);
        return new ContentSource() {
            @Override
            public String name() {
                return "recording";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                int attempt = listCalls.incrementAndGet();
                if (attempt < succeedOnAttempt) {
                    throw new IOException("transient");
                }
                return List.of(new ContentRef("a.txt"));
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.empty();
            }

            @Override
            public InputStream open(ContentRef ref) {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
    }
}
