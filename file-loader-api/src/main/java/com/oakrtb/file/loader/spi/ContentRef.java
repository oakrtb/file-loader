package com.oakrtb.file.loader.spi;

import java.util.Objects;

/**
 * Reference to a remote or local file resource.
 */
public final class ContentRef {

    private final String path;

    public ContentRef(String path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentRef)) {
            return false;
        }
        ContentRef that = (ContentRef) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "ContentRef{path='" + path + "'}";
    }
}
