package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingContentSourceTest {

    @Test
    void retriesTransientListFailuresThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ContentSource flaky = new ContentSource() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                if (calls.incrementAndGet() < 3) {
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

        ContentSource source = RetryingContentSource.wrap(
                flaky, new ObjectStoreClientOptions(1000, 1000, 3, 1));
        assertEquals(1, source.list("").size());
        assertEquals(3, calls.get());
    }

    @Test
    void exhaustsAttemptsAndThrows() {
        AtomicInteger calls = new AtomicInteger();
        ContentSource alwaysFail = new ContentSource() {
            @Override
            public String name() {
                return "fail";
            }

            @Override
            public List<ContentRef> list(String prefix) throws IOException {
                calls.incrementAndGet();
                throw new IOException("always");
            }

            @Override
            public Optional<ContentMeta> meta(ContentRef ref) {
                return Optional.empty();
            }

            @Override
            public InputStream open(ContentRef ref) {
                return InputStream.nullInputStream();
            }
        };

        ContentSource source = RetryingContentSource.wrap(
                alwaysFail, new ObjectStoreClientOptions(1000, 1000, 3, 1));
        assertThrows(IOException.class, () -> source.list(""));
        assertEquals(3, calls.get());
    }
}
