package com.oakrtb.file.loader;

import com.oakrtb.file.loader.config.FileLoaderConfig;
import com.oakrtb.file.loader.source.LocalFsContentSource;
import com.oakrtb.file.loader.source.ObjectStoreConfig;
import com.oakrtb.file.loader.source.ObjectStoreSources;
import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.store.DefaultInMemoryStore;
import com.oakrtb.file.loader.store.InMemoryStore;
import com.oakrtb.file.loader.store.LocalFsWatchRefresh;
import com.oakrtb.file.loader.store.PrefixResolver;
import com.oakrtb.file.loader.store.RefreshListener;
import com.oakrtb.file.loader.store.LoggingRefreshListener;
import com.oakrtb.file.loader.store.RefreshResult;
import com.oakrtb.file.loader.store.RefreshStats;
import com.oakrtb.file.loader.store.ScheduledRefresh;
import com.oakrtb.file.loader.store.SnapshotCache;
import com.oakrtb.file.loader.store.StoreOptions;
import com.oakrtb.file.loader.view.MemoryView;
import com.oakrtb.file.loader.view.ViewRegistration;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade: wire {@link ContentSource} + in-memory store + views + optional scheduled / watch refresh.
 */
public final class FileLoader implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FileLoader.class.getName());
    public static final Duration DEFAULT_WATCH_POLL_FALLBACK = Duration.ofMinutes(1);

    private final ContentSource source;
    private final boolean closeSource;
    private final DefaultInMemoryStore store;
    private final ScheduledRefresh scheduledRefresh;
    private final LocalFsWatchRefresh watchRefresh;
    private final Map<String, MemoryView<?>> views;
    private final RefreshStats stats;
    private final boolean failFast;
    private final boolean cacheFallback;
    private volatile boolean started;

    private FileLoader(
            ContentSource source,
            boolean closeSource,
            DefaultInMemoryStore store,
            ScheduledRefresh scheduledRefresh,
            LocalFsWatchRefresh watchRefresh,
            Map<String, MemoryView<?>> views,
            RefreshStats stats,
            boolean failFast,
            boolean cacheFallback) {
        this.source = source;
        this.closeSource = closeSource;
        this.store = store;
        this.scheduledRefresh = scheduledRefresh;
        this.watchRefresh = watchRefresh;
        this.views = Map.copyOf(views);
        this.stats = stats;
        this.failFast = failFast;
        this.cacheFallback = cacheFallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(FileLoaderConfig config) {
        return new Builder().config(config);
    }

    public synchronized void start() throws Exception {
        if (started) {
            return;
        }
        try {
            RefreshResult result = store.refresh();
            if (!result.isSuccess()) {
                throw toException(result);
            }
        } catch (Exception e) {
            handleStartFailure(e);
        }
        if (scheduledRefresh != null) {
            scheduledRefresh.start();
        }
        if (watchRefresh != null) {
            watchRefresh.start();
        }
        started = true;
    }

    private void handleStartFailure(Exception e) throws Exception {
        if (cacheFallback) {
            LOG.log(Level.WARNING, "initial refresh failed, trying snapshot cache", e);
            store.loadFromCache();
            if (store.current().size() == 0 && failFast) {
                throw e;
            }
            if (store.current().size() == 0) {
                LOG.warning("cache empty after refresh failure; continuing with empty snapshot");
            }
            return;
        }
        if (failFast) {
            throw e;
        }
        LOG.log(Level.WARNING, "initial refresh failed; continuing with empty snapshot", e);
    }

    private static Exception toException(RefreshResult result) {
        Throwable error = result.getError().orElse(null);
        if (error instanceof Exception ex) {
            return ex;
        }
        if (error != null) {
            return new IllegalStateException("refresh failed: " + result, error);
        }
        return new IllegalStateException("refresh failed: " + result);
    }

    public InMemoryStore store() {
        return store;
    }

    public ContentSource source() {
        return source;
    }

    public RefreshStats stats() {
        return stats;
    }

    /** Present when a scheduled poll (including watch poll-fallback) was configured. */
    public Optional<Duration> scheduledRefreshInterval() {
        return scheduledRefresh == null ? Optional.empty() : Optional.of(scheduledRefresh.interval());
    }

    public boolean watchEnabled() {
        return watchRefresh != null;
    }

    @SuppressWarnings("unchecked")
    public <T> MemoryView<T> view(String name) {
        MemoryView<?> view = views.get(name);
        if (view == null) {
            throw new IllegalArgumentException("unknown view: " + name);
        }
        return (MemoryView<T>) view;
    }

    public Optional<MemoryView<?>> findView(String name) {
        return Optional.ofNullable(views.get(name));
    }

    public Map<String, MemoryView<?>> views() {
        return views;
    }

    @Override
    public synchronized void close() {
        if (watchRefresh != null) {
            watchRefresh.close();
        }
        if (scheduledRefresh != null) {
            scheduledRefresh.close();
        }
        if (closeSource && source instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        started = false;
    }

    public static final class Builder {

        private ContentSource source;
        private boolean closeSource = true;
        private PrefixResolver prefixResolver = PrefixResolver.fixed("");
        private String watchRootPrefix = "";
        private Duration refreshInterval;
        private boolean watchLocalChanges;
        private boolean watchPollFallback = true;
        private Duration watchDebounce = Duration.ofMillis(300);
        private Path cacheDir;
        private boolean failFast = true;
        private boolean cacheFallback = false;
        private boolean loggingListener = true;
        private StoreOptions storeOptions = StoreOptions.defaults();
        private final Map<String, MemoryView<?>> views = new LinkedHashMap<>();
        private final List<RefreshListener> listeners = new ArrayList<>();

        public Builder config(FileLoaderConfig config) {
            Objects.requireNonNull(config, "config");
            this.failFast = config.failFast();
            this.cacheFallback = config.cacheFallback();
            this.storeOptions = config.storeOptions();
            this.prefixResolver = config.prefixResolver();
            this.watchRootPrefix = config.watchRootPrefix();
            this.watchLocalChanges = config.watchLocalChanges();
            this.watchPollFallback = config.watchPollFallback();
            config.refreshInterval().ifPresent(interval -> this.refreshInterval = interval);
            config.cacheDir().ifPresent(dir -> this.cacheDir = dir);
            switch (config.sourceType()) {
                case LOCAL -> local(config.localRoot().orElseThrow());
                case OSS -> oss(config.objectStore().orElseThrow());
                case TOS -> tos(config.objectStore().orElseThrow());
            }
            return this;
        }

        public Builder local(Path root) {
            this.source = new LocalFsContentSource(root);
            this.closeSource = false;
            return this;
        }

        public Builder oss(ObjectStoreConfig config) {
            this.source = ObjectStoreSources.oss(config);
            this.closeSource = true;
            return this;
        }

        public Builder tos(ObjectStoreConfig config) {
            this.source = ObjectStoreSources.tos(config);
            this.closeSource = true;
            return this;
        }

        public Builder source(ContentSource source) {
            this.source = Objects.requireNonNull(source, "source");
            this.closeSource = source instanceof AutoCloseable;
            return this;
        }

        public Builder source(ContentSource source, boolean closeSource) {
            this.source = Objects.requireNonNull(source, "source");
            this.closeSource = closeSource;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefixResolver = PrefixResolver.fixed(prefix);
            this.watchRootPrefix = prefix == null ? "" : prefix;
            return this;
        }

        public Builder prefixResolver(PrefixResolver prefixResolver) {
            this.prefixResolver = Objects.requireNonNull(prefixResolver, "prefixResolver");
            return this;
        }

        /**
         * Only load today's folder under base, e.g. {@code task/2026-10-12/}.
         * Watch root stays at {@code task/} so new date dirs are visible.
         */
        public Builder rollingDatePrefix(String basePrefix, String datePattern) {
            this.prefixResolver = PrefixResolver.rollingDate(basePrefix, datePattern);
            this.watchRootPrefix = PrefixResolver.watchRootOf(basePrefix);
            return this;
        }

        public Builder rollingDatePrefix(String basePrefix, DateTimeFormatter formatter, ZoneId zoneId) {
            this.prefixResolver = PrefixResolver.rollingDate(basePrefix, formatter, zoneId);
            this.watchRootPrefix = PrefixResolver.watchRootOf(basePrefix);
            return this;
        }

        public Builder refreshEvery(Duration interval) {
            this.refreshInterval = interval;
            return this;
        }

        public Builder watchLocalChanges(boolean enabled) {
            this.watchLocalChanges = enabled;
            return this;
        }

        /**
         * When watch is enabled and no {@link #refreshEvery} is set, automatically poll as fallback
         * (WatchService can miss events on some OS / network mounts). Default: true.
         */
        public Builder watchPollFallback(boolean enabled) {
            this.watchPollFallback = enabled;
            return this;
        }

        public Builder watchDebounce(Duration debounce) {
            this.watchDebounce = Objects.requireNonNull(debounce, "debounce");
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

        public Builder loggingListener(boolean enabled) {
            this.loggingListener = enabled;
            return this;
        }

        public Builder addListener(RefreshListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public Builder storeOptions(StoreOptions storeOptions) {
            this.storeOptions = Objects.requireNonNull(storeOptions, "storeOptions");
            return this;
        }

        public Builder addView(String name, MemoryView<?> view) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(view, "view");
            if (views.containsKey(name)) {
                throw new IllegalArgumentException("duplicate view name: " + name);
            }
            views.put(name, view);
            return this;
        }

        public Builder addView(ViewRegistration registration) {
            Objects.requireNonNull(registration, "registration");
            return addView(registration.name(), registration.view());
        }

        public Builder addViews(Iterable<ViewRegistration> registrations) {
            Objects.requireNonNull(registrations, "registrations");
            for (ViewRegistration registration : registrations) {
                addView(registration);
            }
            return this;
        }

        public FileLoader build() {
            if (source == null) {
                throw new IllegalStateException("source is required");
            }
            if (watchLocalChanges && !(source instanceof LocalFsContentSource)) {
                throw new IllegalStateException("watchLocalChanges requires LocalFsContentSource");
            }
            Duration effectiveInterval = refreshInterval;
            if (watchLocalChanges && watchPollFallback && effectiveInterval == null) {
                effectiveInterval = DEFAULT_WATCH_POLL_FALLBACK;
                LOG.info("watchLocalChanges enabled without refreshEvery; "
                        + "using poll fallback interval=" + effectiveInterval);
            }
            SnapshotCache cache = cacheDir == null ? null : new SnapshotCache(cacheDir);
            StoreOptions effective = storeOptions;
            if (effective.spillDir() == null && cacheDir != null) {
                effective = StoreOptions.builder()
                        .heapThresholdBytes(storeOptions.heapThresholdBytes())
                        .spillDir(cacheDir.resolve("spill-work"))
                        .downloadConcurrency(storeOptions.downloadConcurrency())
                        .manifestEnabled(storeOptions.manifestEnabled())
                        .manifestName(storeOptions.manifestName())
                        .retainRawContent(storeOptions.retainRawContent())
                        .build();
            }
            DefaultInMemoryStore store = new DefaultInMemoryStore(source, prefixResolver, cache, effective);
            RefreshStats stats = new RefreshStats();
            store.addListener(stats);
            if (loggingListener) {
                store.addListener(new LoggingRefreshListener());
            }
            for (RefreshListener listener : listeners) {
                store.addListener(listener);
            }
            for (MemoryView<?> view : views.values()) {
                store.addView(view);
            }
            ScheduledRefresh refresh = null;
            if (effectiveInterval != null) {
                refresh = new ScheduledRefresh(store, effectiveInterval, false);
            }
            LocalFsWatchRefresh watch = null;
            if (watchLocalChanges) {
                String watchPrefix = watchRootPrefix == null ? "" : watchRootPrefix;
                watch = new LocalFsWatchRefresh(
                        store, (LocalFsContentSource) source, watchPrefix, watchDebounce);
            }
            return new FileLoader(
                    source, closeSource, store, refresh, watch, views, stats, failFast, cacheFallback);
        }
    }
}
