package com.oakrtb.file.loader.store;

import java.util.Optional;

/**
 * Holds the latest loaded content in memory and supports refresh.
 */
public interface InMemoryStore {

    ContentSnapshot current();

    /**
     * Pull latest content from the bound {@link com.oakrtb.file.loader.spi.ContentSource}.
     * Overlapping calls may return a skipped {@link RefreshResult} without throwing.
     */
    RefreshResult refresh() throws Exception;

    Optional<byte[]> get(String path);
}
