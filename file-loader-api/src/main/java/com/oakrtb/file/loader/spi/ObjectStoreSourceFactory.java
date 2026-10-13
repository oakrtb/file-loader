package com.oakrtb.file.loader.spi;

import com.oakrtb.file.loader.source.ObjectStoreConfig;

/**
 * SPI for creating object-storage {@link ContentSource} implementations (OSS / TOS / ...).
 */
public interface ObjectStoreSourceFactory {

    /**
     * Logical type, e.g. {@code oss} or {@code tos}.
     */
    String type();

    ContentSource create(ObjectStoreConfig config);
}
