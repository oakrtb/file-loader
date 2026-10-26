package com.oakrtb.file.loader.source;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Alibaba Cloud OSS {@link ContentSource}. Caller should {@link #close()} when finished.
 */
public final class OssContentSource implements ContentSource, AutoCloseable {

    private final ObjectStoreConfig config;
    private final OSS client;

    public OssContentSource(ObjectStoreConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        ObjectStoreClientOptions opts = config.clientOptions();
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setConnectionTimeout(opts.connectTimeoutMs());
        conf.setSocketTimeout(opts.socketTimeoutMs());
        conf.setRequestTimeoutEnabled(true);
        conf.setRequestTimeout(opts.socketTimeoutMs());
        conf.setMaxErrorRetry(opts.maxRetries());
        this.client = new OSSClientBuilder().build(
                config.endpoint(), config.accessKey(), config.accessSecret(), conf);
    }

    /** For tests / custom client lifecycle. */
    public OssContentSource(ObjectStoreConfig config, OSS client) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String name() {
        return "oss";
    }

    @Override
    public List<ContentRef> list(String prefix) throws IOException {
        String normalized = normalizePrefix(prefix);
        List<ContentRef> refs = new ArrayList<>();
        try {
            String marker = null;
            ObjectListing listing;
            do {
                ListObjectsRequest request = new ListObjectsRequest(config.bucket())
                        .withPrefix(normalized)
                        .withMarker(marker)
                        .withMaxKeys(1000);
                listing = client.listObjects(request);
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    if (isUsefulObject(summary.getKey(), summary.getSize())) {
                        refs.add(new ContentRef(summary.getKey()));
                    }
                }
                marker = listing.getNextMarker();
            } while (listing.isTruncated());
            return refs;
        } catch (OSSException e) {
            throw new IOException("oss list failed: prefix=" + normalized, e);
        } catch (RuntimeException e) {
            throw new IOException("oss list failed: prefix=" + normalized, e);
        }
    }

    @Override
    public Optional<ContentMeta> meta(ContentRef ref) throws IOException {
        try {
            ObjectMetadata metadata = client.getObjectMetadata(config.bucket(), ref.getPath());
            return Optional.of(toMeta(metadata));
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw new IOException("oss meta failed: path=" + ref.getPath(), e);
        } catch (RuntimeException e) {
            throw new IOException("oss meta failed: path=" + ref.getPath(), e);
        }
    }

    @Override
    public InputStream open(ContentRef ref) throws IOException {
        try {
            OSSObject object = client.getObject(config.bucket(), ref.getPath());
            if (object == null || object.getObjectContent() == null) {
                throw new IOException("oss object missing: " + ref.getPath());
            }
            return object.getObjectContent();
        } catch (OSSException e) {
            throw new IOException("oss open failed: path=" + ref.getPath(), e);
        } catch (RuntimeException e) {
            throw new IOException("oss open failed: path=" + ref.getPath(), e);
        }
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private static ContentMeta toMeta(ObjectMetadata metadata) {
        Date lastModified = metadata.getLastModified();
        Instant modified = lastModified == null ? null : lastModified.toInstant();
        return new ContentMeta(metadata.getETag(), modified, metadata.getContentLength());
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String p = prefix;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    private static boolean isUsefulObject(String key, long size) {
        return key != null && !key.endsWith("/") && size > 0;
    }
}
