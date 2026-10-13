package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retries transient {@link IOException}s for list/meta/open.
 * Does not retry after an {@link InputStream} has been successfully returned.
 */
public final class RetryingContentSource implements ContentSource, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RetryingContentSource.class.getName());

    private final ContentSource delegate;
    private final ObjectStoreClientOptions options;

    public RetryingContentSource(ContentSource delegate, ObjectStoreClientOptions options) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.options = Objects.requireNonNull(options, "options");
    }

    public static ContentSource wrap(ContentSource delegate, ObjectStoreClientOptions options) {
        if (delegate instanceof RetryingContentSource) {
            return delegate;
        }
        return new RetryingContentSource(delegate, options);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public List<ContentRef> list(String prefix) throws IOException {
        return call(() -> delegate.list(prefix));
    }

    @Override
    public Optional<ContentMeta> meta(ContentRef ref) throws IOException {
        return call(() -> delegate.meta(ref));
    }

    @Override
    public InputStream open(ContentRef ref) throws IOException {
        return call(() -> delegate.open(ref));
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private <T> T call(IoCallable<T> action) throws IOException {
        int attempts = options.maxAttempts();
        IOException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return action.call();
            } catch (IOException e) {
                last = e;
                if (i >= attempts) {
                    break;
                }
                LOG.log(Level.WARNING, "content-source call failed, retrying attempt="
                        + i + "/" + attempts + " source=" + name(), e);
                sleep(options.retryBackoffMs() * i);
            }
        }
        throw last;
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface IoCallable<T> {
        T call() throws IOException;
    }
}
