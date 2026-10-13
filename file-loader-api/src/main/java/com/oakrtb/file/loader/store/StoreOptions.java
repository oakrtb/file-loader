package com.oakrtb.file.loader.store;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Performance / consistency knobs for {@link DefaultInMemoryStore}.
 */
public final class StoreOptions {

    public static final long DEFAULT_HEAP_THRESHOLD_BYTES = 4L * 1024 * 1024;
    public static final int DEFAULT_DOWNLOAD_CONCURRENCY = 4;
    public static final String DEFAULT_MANIFEST_NAME = "manifest.json";

    private final long heapThresholdBytes;
    private final Path spillDir;
    private final int downloadConcurrency;
    private final boolean manifestEnabled;
    private final String manifestName;
    private final boolean retainRawContent;

    private StoreOptions(Builder builder) {
        this.heapThresholdBytes = builder.heapThresholdBytes;
        this.spillDir = builder.spillDir;
        this.downloadConcurrency = builder.downloadConcurrency;
        this.manifestEnabled = builder.manifestEnabled;
        this.manifestName = builder.manifestName;
        this.retainRawContent = builder.retainRawContent;
        if (heapThresholdBytes < 0) {
            throw new IllegalArgumentException("heapThresholdBytes must be >= 0");
        }
        if (downloadConcurrency < 1) {
            throw new IllegalArgumentException("downloadConcurrency must be >= 1");
        }
        Objects.requireNonNull(manifestName, "manifestName");
    }

    public static StoreOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long heapThresholdBytes() {
        return heapThresholdBytes;
    }

    public Path spillDir() {
        return spillDir;
    }

    public int downloadConcurrency() {
        return downloadConcurrency;
    }

    public boolean manifestEnabled() {
        return manifestEnabled;
    }

    public String manifestName() {
        return manifestName;
    }

    /**
     * When {@code false}, after views parse a refresh the store keeps only {@link ContentMeta}
     * (drops heap bytes / spill files) to save memory. Views must keep their own parsed data
     * across refreshes when content is absent (built-in views do).
     */
    public boolean retainRawContent() {
        return retainRawContent;
    }

    public static final class Builder {
        private long heapThresholdBytes = DEFAULT_HEAP_THRESHOLD_BYTES;
        private Path spillDir;
        private int downloadConcurrency = DEFAULT_DOWNLOAD_CONCURRENCY;
        private boolean manifestEnabled = false;
        private String manifestName = DEFAULT_MANIFEST_NAME;
        private boolean retainRawContent = true;

        public Builder heapThresholdBytes(long heapThresholdBytes) {
            this.heapThresholdBytes = heapThresholdBytes;
            return this;
        }

        public Builder spillDir(Path spillDir) {
            this.spillDir = spillDir;
            return this;
        }

        public Builder downloadConcurrency(int downloadConcurrency) {
            this.downloadConcurrency = downloadConcurrency;
            return this;
        }

        public Builder manifestEnabled(boolean manifestEnabled) {
            this.manifestEnabled = manifestEnabled;
            return this;
        }

        public Builder manifestName(String manifestName) {
            this.manifestName = manifestName;
            return this;
        }

        public Builder retainRawContent(boolean retainRawContent) {
            this.retainRawContent = retainRawContent;
            return this;
        }

        public StoreOptions build() {
            return new StoreOptions(this);
        }
    }
}
