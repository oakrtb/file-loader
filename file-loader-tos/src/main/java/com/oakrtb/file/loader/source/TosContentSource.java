package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosException;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.GetObjectV2Output;
import com.volcengine.tos.model.object.HeadObjectV2Input;
import com.volcengine.tos.model.object.HeadObjectV2Output;
import com.volcengine.tos.model.object.ListObjectsType2Input;
import com.volcengine.tos.model.object.ListObjectsType2Output;
import com.volcengine.tos.model.object.ListedObjectV2;
import com.volcengine.tos.transport.TransportConfig;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Volcengine TOS {@link ContentSource}. Caller should {@link #close()} when finished.
 */
public final class TosContentSource implements ContentSource, AutoCloseable {

    private final ObjectStoreConfig config;
    private final TOSV2 client;
    private final boolean ownedClient;

    public TosContentSource(ObjectStoreConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        if (config.region() == null || config.region().isBlank()) {
            throw new IllegalArgumentException("TOS requires region");
        }
        ObjectStoreClientOptions opts = config.clientOptions();
        TransportConfig transport = TransportConfig.builder()
                .connectTimeoutMills(opts.connectTimeoutMs())
                .readTimeoutMills(opts.socketTimeoutMs())
                .writeTimeoutMills(opts.socketTimeoutMs())
                .maxRetryCount(opts.maxRetries())
                .build();
        this.client = new TOSV2ClientBuilder().build(
                config.region(),
                config.endpoint(),
                config.accessKey(),
                config.accessSecret(),
                transport);
        this.ownedClient = true;
    }

    /** For tests / custom client lifecycle. */
    public TosContentSource(ObjectStoreConfig config, TOSV2 client) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.ownedClient = false;
    }

    @Override
    public String name() {
        return "tos";
    }

    @Override
    public List<ContentRef> list(String prefix) throws IOException {
        String normalized = normalizePrefix(prefix);
        List<ContentRef> refs = new ArrayList<>();
        try {
            String token = null;
            ListObjectsType2Output output;
            do {
                ListObjectsType2Input input = new ListObjectsType2Input()
                        .setBucket(config.bucket())
                        .setPrefix(normalized)
                        .setMaxKeys(1000)
                        .setContinuationToken(token);
                output = client.listObjectsType2(input);
                if (output.getContents() != null) {
                    for (ListedObjectV2 obj : output.getContents()) {
                        if (isUsefulObject(obj.getKey(), obj.getSize())) {
                            refs.add(new ContentRef(obj.getKey()));
                        }
                    }
                }
                token = output.getNextContinuationToken();
            } while (output.isTruncated());
            return refs;
        } catch (TosException e) {
            throw new IOException("tos list failed: prefix=" + normalized, e);
        } catch (RuntimeException e) {
            throw new IOException("tos list failed: prefix=" + normalized, e);
        }
    }

    @Override
    public Optional<ContentMeta> meta(ContentRef ref) throws IOException {
        try {
            HeadObjectV2Output output = client.headObject(new HeadObjectV2Input()
                    .setBucket(config.bucket())
                    .setKey(ref.getPath()));
            return Optional.of(toMeta(output));
        } catch (TosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw new IOException("tos meta failed: path=" + ref.getPath(), e);
        } catch (RuntimeException e) {
            throw new IOException("tos meta failed: path=" + ref.getPath(), e);
        }
    }

    @Override
    public InputStream open(ContentRef ref) throws IOException {
        try {
            GetObjectV2Output output = client.getObject(new GetObjectV2Input()
                    .setBucket(config.bucket())
                    .setKey(ref.getPath()));
            if (output == null || output.getContent() == null) {
                throw new IOException("tos object missing: " + ref.getPath());
            }
            return output.getContent();
        } catch (TosException e) {
            throw new IOException("tos open failed: path=" + ref.getPath(), e);
        } catch (RuntimeException e) {
            throw new IOException("tos open failed: path=" + ref.getPath(), e);
        }
    }

    @Override
    public void close() {
        if (!ownedClient) {
            return;
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static ContentMeta toMeta(HeadObjectV2Output output) {
        Date lastModified = output.getLastModifiedInDate();
        Instant modified = lastModified == null ? null : lastModified.toInstant();
        return new ContentMeta(output.getEtag(), modified, output.getContentLength());
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
