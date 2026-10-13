package com.oakrtb.file.loader.config;

import com.oakrtb.file.loader.source.ObjectStoreClientOptions;
import com.oakrtb.file.loader.source.ObjectStoreConfig;
import com.oakrtb.file.loader.store.PrefixResolver;
import com.oakrtb.file.loader.store.StoreOptions;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Runtime configuration for {@link com.oakrtb.file.loader.FileLoader}.
 */
public final class FileLoaderConfig {

    public enum SourceType {
        LOCAL, OSS, TOS
    }

    private final SourceType sourceType;
    private final Path localRoot;
    private final ObjectStoreConfig objectStore;
    private final PrefixResolver prefixResolver;
    private final String watchRootPrefix;
    private final Duration refreshInterval;
    private final Path cacheDir;
    private final boolean failFast;
    private final boolean cacheFallback;
    private final boolean watchLocalChanges;
    private final boolean watchPollFallback;
    private final StoreOptions storeOptions;

    private FileLoaderConfig(Builder builder) {
        this.sourceType = Objects.requireNonNull(builder.sourceType, "sourceType");
        this.localRoot = builder.localRoot;
        this.objectStore = builder.objectStore;
        this.prefixResolver = builder.prefixResolver == null
                ? PrefixResolver.fixed(builder.prefix)
                : builder.prefixResolver;
        this.watchRootPrefix = builder.watchRootPrefix != null
                ? builder.watchRootPrefix
                : (builder.prefix == null ? "" : builder.prefix);
        this.refreshInterval = builder.refreshInterval;
        this.cacheDir = builder.cacheDir;
        this.failFast = builder.failFast;
        this.cacheFallback = builder.cacheFallback;
        this.watchLocalChanges = builder.watchLocalChanges;
        this.watchPollFallback = builder.watchPollFallback;
        this.storeOptions = builder.storeOptions == null ? StoreOptions.defaults() : builder.storeOptions;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FileLoaderConfig fromEnv() {
        return from(System::getenv, key -> null);
    }

    public static FileLoaderConfig fromProperties(Properties props) {
        Objects.requireNonNull(props, "props");
        return from(props::getProperty, System::getenv);
    }

    public static FileLoaderConfig from(Function<String, String> primary, Function<String, String> fallback) {
        Function<String, String> get = key -> {
            String v = primary.apply(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
            String alt = fallback.apply(key);
            return alt == null || alt.isBlank() ? null : alt.trim();
        };

        String type = Optional.ofNullable(get.apply("FILE_LOADER_SOURCE"))
                .orElse("local")
                .toLowerCase(Locale.ROOT);

        StoreOptions.Builder store = StoreOptions.builder();
        String heap = get.apply("FILE_LOADER_HEAP_THRESHOLD_BYTES");
        if (heap != null) {
            store.heapThresholdBytes(Long.parseLong(heap));
        }
        String spill = get.apply("FILE_LOADER_SPILL_DIR");
        if (spill != null) {
            store.spillDir(Path.of(spill));
        }
        String concurrency = get.apply("FILE_LOADER_DOWNLOAD_CONCURRENCY");
        if (concurrency != null) {
            store.downloadConcurrency(Integer.parseInt(concurrency));
        }
        store.manifestEnabled(parseBoolean(get.apply("FILE_LOADER_MANIFEST_ENABLED"), false));
        String manifestName = get.apply("FILE_LOADER_MANIFEST_NAME");
        if (manifestName != null) {
            store.manifestName(manifestName);
        }
        store.retainRawContent(parseBoolean(get.apply("FILE_LOADER_RETAIN_RAW_CONTENT"), true));

        Builder b = builder()
                .failFast(parseBoolean(get.apply("FILE_LOADER_FAIL_FAST"), true))
                .cacheFallback(parseBoolean(get.apply("FILE_LOADER_CACHE_FALLBACK"), false))
                .watchLocalChanges(parseBoolean(get.apply("FILE_LOADER_WATCH_LOCAL"), false))
                .watchPollFallback(parseBoolean(get.apply("FILE_LOADER_WATCH_POLL_FALLBACK"), true))
                .storeOptions(store.build());

        String rollingBase = get.apply("FILE_LOADER_ROLLING_DATE_PREFIX");
        if (rollingBase != null) {
            String pattern = Optional.ofNullable(get.apply("FILE_LOADER_DATE_PATTERN")).orElse("yyyy-MM-dd");
            b.rollingDatePrefix(rollingBase, pattern);
        } else {
            b.prefix(Optional.ofNullable(get.apply("FILE_LOADER_PREFIX")).orElse(""));
        }

        String interval = get.apply("FILE_LOADER_REFRESH_INTERVAL");
        if (interval != null) {
            b.refreshInterval(parseDuration(interval));
        }
        String cache = get.apply("FILE_LOADER_CACHE_DIR");
        if (cache != null) {
            b.cacheDir(Path.of(cache));
        }

        ObjectStoreClientOptions clientOptions = parseClientOptions(get);

        return switch (type) {
            case "oss" -> b.sourceType(SourceType.OSS)
                    .objectStore(ObjectStoreConfig.oss(
                            required(get, "FILE_LOADER_ENDPOINT"),
                            required(get, "FILE_LOADER_ACCESS_KEY"),
                            required(get, "FILE_LOADER_ACCESS_SECRET"),
                            required(get, "FILE_LOADER_BUCKET"),
                            clientOptions))
                    .build();
            case "tos" -> b.sourceType(SourceType.TOS)
                    .objectStore(ObjectStoreConfig.tos(
                            required(get, "FILE_LOADER_REGION"),
                            required(get, "FILE_LOADER_ENDPOINT"),
                            required(get, "FILE_LOADER_ACCESS_KEY"),
                            required(get, "FILE_LOADER_ACCESS_SECRET"),
                            required(get, "FILE_LOADER_BUCKET"),
                            clientOptions))
                    .build();
            case "local" -> b.sourceType(SourceType.LOCAL)
                    .localRoot(Path.of(Optional.ofNullable(get.apply("FILE_LOADER_LOCAL_ROOT")).orElse(".")))
                    .build();
            default -> throw new IllegalArgumentException("unsupported FILE_LOADER_SOURCE: " + type);
        };
    }

    private static ObjectStoreClientOptions parseClientOptions(Function<String, String> get) {
        ObjectStoreClientOptions defaults = ObjectStoreClientOptions.defaults();
        int connect = parseInt(get.apply("FILE_LOADER_CONNECT_TIMEOUT_MS"), defaults.connectTimeoutMs());
        int socket = parseInt(get.apply("FILE_LOADER_SOCKET_TIMEOUT_MS"), defaults.socketTimeoutMs());
        int attempts = parseInt(get.apply("FILE_LOADER_MAX_ATTEMPTS"), defaults.maxAttempts());
        long backoff = parseLong(get.apply("FILE_LOADER_RETRY_BACKOFF_MS"), defaults.retryBackoffMs());
        return new ObjectStoreClientOptions(connect, socket, attempts, backoff);
    }

    public SourceType sourceType() {
        return sourceType;
    }

    public Optional<Path> localRoot() {
        return Optional.ofNullable(localRoot);
    }

    public Optional<ObjectStoreConfig> objectStore() {
        return Optional.ofNullable(objectStore);
    }

    public PrefixResolver prefixResolver() {
        return prefixResolver;
    }

    public String watchRootPrefix() {
        return watchRootPrefix;
    }

    public Optional<Duration> refreshInterval() {
        return Optional.ofNullable(refreshInterval);
    }

    public Optional<Path> cacheDir() {
        return Optional.ofNullable(cacheDir);
    }

    public boolean failFast() {
        return failFast;
    }

    public boolean cacheFallback() {
        return cacheFallback;
    }

    public boolean watchLocalChanges() {
        return watchLocalChanges;
    }

    public boolean watchPollFallback() {
        return watchPollFallback;
    }

    public StoreOptions storeOptions() {
        return storeOptions;
    }

    private void validate() {
        if (sourceType == SourceType.LOCAL && localRoot == null) {
            throw new IllegalArgumentException("localRoot required for LOCAL");
        }
        if ((sourceType == SourceType.OSS || sourceType == SourceType.TOS) && objectStore == null) {
            throw new IllegalArgumentException("objectStore required for " + sourceType);
        }
    }

    private static String required(Function<String, String> get, String key) {
        String value = get.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required config: " + key);
        }
        return value;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private static Duration parseDuration(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2).trim()));
        }
        if (v.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(v.substring(0, v.length() - 1).trim()));
        }
        if (v.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(v.substring(0, v.length() - 1).trim()));
        }
        if (v.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(v.substring(0, v.length() - 1).trim()));
        }
        return Duration.ofSeconds(Long.parseLong(v));
    }

    public static final class Builder {
        private SourceType sourceType = SourceType.LOCAL;
        private Path localRoot;
        private ObjectStoreConfig objectStore;
        private String prefix = "";
        private PrefixResolver prefixResolver;
        private String watchRootPrefix;
        private Duration refreshInterval;
        private Path cacheDir;
        private boolean failFast = true;
        private boolean cacheFallback = false;
        private boolean watchLocalChanges = false;
        private boolean watchPollFallback = true;
        private StoreOptions storeOptions;

        public Builder sourceType(SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder localRoot(Path localRoot) {
            this.localRoot = localRoot;
            return this;
        }

        public Builder objectStore(ObjectStoreConfig objectStore) {
            this.objectStore = objectStore;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            this.prefixResolver = PrefixResolver.fixed(prefix);
            this.watchRootPrefix = prefix == null ? "" : prefix;
            return this;
        }

        public Builder rollingDatePrefix(String basePrefix, String datePattern) {
            this.prefixResolver = PrefixResolver.rollingDate(basePrefix, datePattern);
            this.watchRootPrefix = PrefixResolver.watchRootOf(basePrefix);
            this.prefix = watchRootPrefix;
            return this;
        }

        public Builder refreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
            return this;
        }

        public Builder cacheDir(Path cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public Builder cacheFallback(boolean cacheFallback) {
            this.cacheFallback = cacheFallback;
            return this;
        }

        public Builder watchLocalChanges(boolean watchLocalChanges) {
            this.watchLocalChanges = watchLocalChanges;
            return this;
        }

        public Builder watchPollFallback(boolean watchPollFallback) {
            this.watchPollFallback = watchPollFallback;
            return this;
        }

        public Builder storeOptions(StoreOptions storeOptions) {
            this.storeOptions = storeOptions;
            return this;
        }

        public FileLoaderConfig build() {
            return new FileLoaderConfig(this);
        }
    }
}
