package com.oakrtb.file.loader.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * SPI for reading file resources from OSS / TOS / local FS / etc.
 */
public interface ContentSource {

    /**
     * Logical source name, e.g. {@code oss}, {@code tos}, {@code local}.
     */
    String name();

    /**
     * List resources under a prefix (inclusive of the prefix itself when it is a file).
     */
    List<ContentRef> list(String prefix) throws IOException;

    /**
     * Read metadata for change detection; empty if the resource does not exist.
     */
    Optional<ContentMeta> meta(ContentRef ref) throws IOException;

    /**
     * Open a stream to the resource content. Caller must close the stream.
     */
    InputStream open(ContentRef ref) throws IOException;
}
