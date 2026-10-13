package com.oakrtb.file.loader.view;

import java.util.Objects;

/**
 * Business payload together with the logical source path it was loaded from.
 *
 * @param path  logical path in the snapshot, e.g. {@code conf/users.csv}
 * @param value parsed business object
 * @param <T>   value type
 */
public record SourcedValue<T>(String path, T value) {

    public SourcedValue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
    }
}
