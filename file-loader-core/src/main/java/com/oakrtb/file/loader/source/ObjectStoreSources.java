package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.spi.ObjectStoreSourceFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves object-store {@link ContentSource} via {@link ServiceLoader}.
 * Requires {@code file-loader-oss} / {@code file-loader-tos} (or custom factories) on the classpath.
 * Applies {@link RetryingContentSource} using {@link ObjectStoreConfig#clientOptions()}.
 */
public final class ObjectStoreSources {

    private ObjectStoreSources() {
    }

    public static ContentSource create(String type, ObjectStoreConfig config) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(config, "config");
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        ContentSource source = find(normalized)
                .orElseThrow(() -> new IllegalStateException(
                        "No ObjectStoreSourceFactory for type='" + normalized
                                + "'. Add file-loader-oss / file-loader-tos (or a custom factory) to the classpath."))
                .create(config);
        return RetryingContentSource.wrap(source, config.clientOptions());
    }

    public static ContentSource oss(ObjectStoreConfig config) {
        return create("oss", config);
    }

    public static ContentSource tos(ObjectStoreConfig config) {
        return create("tos", config);
    }

    public static Optional<ObjectStoreSourceFactory> find(String type) {
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        for (ObjectStoreSourceFactory factory : ServiceLoader.load(ObjectStoreSourceFactory.class)) {
            if (normalized.equals(factory.type().toLowerCase(Locale.ROOT))) {
                return Optional.of(factory);
            }
        }
        return Optional.empty();
    }
}
