package com.oakrtb.file.loader.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata used for change detection (etag / lastModified / size).
 */
public final class ContentMeta {

    private final String etag;
    private final Instant lastModified;
    private final long size;

    public ContentMeta(String etag, Instant lastModified, long size) {
        this.etag = etag;
        this.lastModified = lastModified;
        this.size = size;
    }

    public String getEtag() {
        return etag;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public long getSize() {
        return size;
    }

    public boolean sameVersion(ContentMeta other) {
        if (other == null) {
            return false;
        }
        if (etag != null && other.etag != null) {
            return etag.equals(other.etag);
        }
        if (lastModified != null && other.lastModified != null) {
            return lastModified.equals(other.lastModified) && size == other.size;
        }
        return size == other.size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentMeta)) {
            return false;
        }
        ContentMeta that = (ContentMeta) o;
        return size == that.size
                && Objects.equals(etag, that.etag)
                && Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(etag, lastModified, size);
    }

    @Override
    public String toString() {
        return "ContentMeta{etag='" + etag + "', lastModified=" + lastModified + ", size=" + size + '}';
    }
}
