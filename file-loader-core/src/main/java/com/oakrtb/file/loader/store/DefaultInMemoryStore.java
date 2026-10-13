package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.view.MemoryView;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default store with incremental meta refresh, optional parallel download,
 * large-file spill, and optional manifest atomic switch.
 */
public final class DefaultInMemoryStore implements InMemoryStore {

    private static final Logger LOG = Logger.getLogger(DefaultInMemoryStore.class.getName());

    private final ContentSource source;
    private final PrefixResolver prefixResolver;
    private final SnapshotCache cache;
    private final StoreOptions options;
    private final ContentMaterializer materializer;
    private final ManifestParser manifestParser;
    private final AtomicReference<ContentSnapshot> snapshot = new AtomicReference<>(ContentSnapshot.empty());
    private final List<MemoryView<?>> views = new CopyOnWriteArrayList<>();
    private final List<RefreshListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    /** Set when refresh() is skipped due to overlap; the in-flight refresh coalesces a follow-up. */
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    public DefaultInMemoryStore(ContentSource source, String prefix) {
        this(source, PrefixResolver.fixed(prefix), null, StoreOptions.defaults());
    }

    public DefaultInMemoryStore(ContentSource source, String prefix, SnapshotCache cache) {
        this(source, PrefixResolver.fixed(prefix), cache, StoreOptions.defaults());
    }

    public DefaultInMemoryStore(ContentSource source, String prefix, SnapshotCache cache, StoreOptions options) {
        this(source, PrefixResolver.fixed(prefix), cache, options);
    }

    public DefaultInMemoryStore(
            ContentSource source, PrefixResolver prefixResolver, SnapshotCache cache, StoreOptions options) {
        this.source = Objects.requireNonNull(source, "source");
        this.prefixResolver = Objects.requireNonNull(prefixResolver, "prefixResolver");
        this.cache = cache;
        this.options = Objects.requireNonNull(options, "options");
        this.materializer = new ContentMaterializer(options);
        this.manifestParser = new ManifestParser();
    }

    public void addView(MemoryView<?> view) {
        views.add(Objects.requireNonNull(view, "view"));
        view.onSnapshot(current());
    }

    public void addListener(RefreshListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void loadFromCache() throws Exception {
        if (cache == null || !cache.exists()) {
            return;
        }
        ContentSnapshot cached = cache.load(options);
        if (cached.size() == 0) {
            return;
        }
        snapshot.set(cached);
        notifyViews(cached);
    }

    @Override
    public ContentSnapshot current() {
        return snapshot.get();
    }

    @Override
    public RefreshResult refresh() throws Exception {
        Instant start = Instant.now();
        if (!refreshing.compareAndSet(false, true)) {
            refreshPending.set(true);
            RefreshResult skipped = RefreshResult.skipped(Duration.between(start, Instant.now()));
            notifyListeners(skipped);
            return skipped;
        }
        RefreshResult last = null;
        Exception failure = null;
        try {
            do {
                refreshPending.set(false);
                Instant roundStart = Instant.now();
                ContentSnapshot previous = snapshot.get();
                if (options.manifestEnabled()) {
                    last = refreshWithManifest(roundStart, previous);
                } else {
                    last = refreshAllListed(roundStart, previous);
                }
            } while (refreshPending.get());
        } catch (Exception e) {
            failure = e;
            RefreshResult failed = RefreshResult.failed(Duration.between(start, Instant.now()), e);
            notifyListeners(failed);
        } finally {
            refreshing.set(false);
        }
        // Race: pending set after the last while-check and before refreshing cleared.
        // Also run after a thrown failure so an overlapping skip is not lost.
        if (refreshPending.get()) {
            RefreshResult again = refresh();
            if (failure == null) {
                return again.isSkipped() ? last : again;
            }
            if (!again.isSkipped()) {
                return again;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return last;
    }

    private RefreshResult refreshWithManifest(Instant start, ContentSnapshot previous) throws Exception {
        String prefix = prefixResolver.resolve();
        String manifestPath = joinPath(prefix, options.manifestName());
        ContentRef manifestRef = new ContentRef(manifestPath);
        OneLoad manifestLoad = loadOne(manifestRef, previous);
        if (manifestLoad.entry == null) {
            IOException error = new IOException("manifest missing or unreadable: " + manifestPath);
            RefreshResult failed = RefreshResult.failed(Duration.between(start, Instant.now()), error);
            notifyListeners(failed);
            return failed;
        }
        // retainRawContent=false: reused entry is meta-only — re-fetch body so we can still
        // parse paths and run containsAllPaths (do not short-circuit on etag alone).
        if (!manifestLoad.entry.hasContent()) {
            manifestLoad = loadOne(manifestRef, ContentSnapshot.empty());
            if (manifestLoad.entry == null || !manifestLoad.entry.hasContent()) {
                IOException error = new IOException("manifest missing or unreadable: " + manifestPath);
                RefreshResult failed = RefreshResult.failed(Duration.between(start, Instant.now()), error);
                notifyListeners(failed);
                return failed;
            }
        }
        Manifest manifest = manifestParser.parse(manifestLoad.entry.getBytes());
        if (previous.getManifestVersion().filter(v -> v.equals(manifest.getVersion())).isPresent()
                && containsAllPaths(previous.getEntries(), manifest.paths())) {
            RefreshResult reused = RefreshResult.ok(
                    Duration.between(start, Instant.now()),
                    manifest.getFiles().size() + 1,
                    0,
                    previous.size(),
                    0,
                    previous.size(),
                    previous.getLoadedAt());
            notifyListeners(reused);
            return reused;
        }

        List<ContentRef> refs = new ArrayList<>();
        refs.add(new ContentRef(manifestPath));
        for (String path : manifest.paths()) {
            refs.add(new ContentRef(path));
        }

        LoadBatch batch = loadBatch(refs, previous);
        if (batch.hardFailures > 0
                || !batch.entries.containsKey(manifestPath)
                || !containsAllPaths(batch.entries, manifest.paths())) {
            IOException error = new IOException(
                    "manifest refresh incomplete; keeping previous snapshot version="
                            + previous.getManifestVersion().orElse("<none>"));
            RefreshResult failed = RefreshResult.failed(Duration.between(start, Instant.now()), error);
            notifyListeners(failed);
            return failed;
        }

        Map<String, ContentEntry> atomic = new LinkedHashMap<>();
        atomic.put(manifestPath, batch.entries.get(manifestPath));
        for (String path : manifest.paths()) {
            atomic.put(path, batch.entries.get(path));
        }
        return commit(start, refs.size(), batch, atomic, manifest.getVersion());
    }

    private RefreshResult refreshAllListed(Instant start, ContentSnapshot previous) throws Exception {
        String prefix = prefixResolver.resolve();
        List<ContentRef> refs = source.list(prefix);
        // Rolling-date (or any prefix change): empty list under a new prefix must not wipe a
        // still-valid previous snapshot whose paths live under the old prefix.
        if (refs.isEmpty() && previous.size() > 0 && !anyPathUnderPrefix(previous, prefix)) {
            LOG.log(Level.WARNING,
                    "list empty for prefix=" + prefix + "; keeping previous snapshot size=" + previous.size());
            RefreshResult kept = RefreshResult.ok(
                    Duration.between(start, Instant.now()),
                    0,
                    0,
                    previous.size(),
                    0,
                    previous.size(),
                    previous.getLoadedAt());
            notifyListeners(kept);
            return kept;
        }
        LoadBatch batch = loadBatch(refs, previous);
        return commit(start, refs.size(), batch, batch.entries, previous.getManifestVersion().orElse(null));
    }

    private RefreshResult commit(
            Instant start,
            int listed,
            LoadBatch batch,
            Map<String, ContentEntry> nextEntries,
            String manifestVersion) {
        ContentSnapshot updated = new ContentSnapshot(Instant.now(), nextEntries, manifestVersion);
        snapshot.set(updated);
        notifyViews(updated);
        if (cache != null) {
            try {
                cache.save(updated);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "snapshot cache save failed", e);
            }
        }
        SpillCleaner.cleanup(options.spillDir(), updated);
        if (!options.retainRawContent()) {
            ContentSnapshot slim = stripRawContent(updated);
            snapshot.set(slim);
            SpillCleaner.cleanup(options.spillDir(), slim);
            updated = slim;
        }
        RefreshResult result = RefreshResult.ok(
                Duration.between(start, Instant.now()),
                listed,
                batch.downloaded,
                batch.reused,
                batch.softFailures + batch.hardFailures,
                updated.size(),
                updated.getLoadedAt());
        notifyListeners(result);
        return result;
    }

    private ContentSnapshot stripRawContent(ContentSnapshot snap) {
        Path spillRoot = spillFilesRoot();
        Map<String, ContentEntry> slim = new LinkedHashMap<>();
        for (Map.Entry<String, ContentEntry> e : snap.getEntries().entrySet()) {
            ContentEntry entry = e.getValue();
            slim.put(e.getKey(), ContentEntry.ofMeta(entry.getMeta()));
            entry.getFile().ifPresent(file -> deleteSpillOnly(file, spillRoot));
        }
        return new ContentSnapshot(
                snap.getLoadedAt(), slim, snap.getManifestVersion().orElse(null));
    }

    private Path spillFilesRoot() {
        if (options.spillDir() == null) {
            return null;
        }
        return options.spillDir().toAbsolutePath().normalize().resolve("spill");
    }

    /**
     * Deletes backing files only under the spill directory. Never deletes SnapshotCache files
     * (or any other ofFile path outside spill).
     */
    private void deleteSpillOnly(Path file, Path spillRoot) {
        if (spillRoot == null || file == null) {
            return;
        }
        Path normalized = file.toAbsolutePath().normalize();
        if (cache != null && cache.isCachedFile(normalized)) {
            return;
        }
        if (!normalized.startsWith(spillRoot)) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(normalized);
        } catch (IOException ex) {
            LOG.log(Level.FINE, "failed to delete spill after strip: " + normalized, ex);
        }
    }

    private static boolean anyPathUnderPrefix(ContentSnapshot snapshot, String prefix) {
        String normalized = prefix == null ? "" : prefix;
        if (normalized.isEmpty()) {
            return snapshot.size() > 0;
        }
        for (String path : snapshot.getEntries().keySet()) {
            if (pathUnderPrefix(path, normalized)) {
                return true;
            }
        }
        return false;
    }

    /** Path-boundary aware: {@code conf} matches {@code conf/a} but not {@code config/a}. */
    static boolean pathUnderPrefix(String path, String prefix) {
        if (path == null || prefix == null || prefix.isEmpty()) {
            return false;
        }
        if (!path.startsWith(prefix)) {
            return false;
        }
        if (prefix.endsWith("/") || path.length() == prefix.length()) {
            return true;
        }
        return path.charAt(prefix.length()) == '/';
    }

    private LoadBatch loadBatch(List<ContentRef> refs, ContentSnapshot previous) {
        Map<String, ContentEntry> next = new LinkedHashMap<>();
        AtomicInteger downloaded = new AtomicInteger();
        AtomicInteger reused = new AtomicInteger();
        AtomicInteger softFailures = new AtomicInteger();
        AtomicInteger hardFailures = new AtomicInteger();

        int concurrency = Math.min(options.downloadConcurrency(), Math.max(1, refs.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "file-loader-download");
            t.setDaemon(true);
            return t;
        });
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(refs.size());
            Object lock = new Object();
            for (ContentRef ref : refs) {
                futures.add(CompletableFuture.runAsync(() -> {
                    OneLoad loaded = loadOne(ref, previous);
                    switch (loaded.kind) {
                        case REUSED -> reused.incrementAndGet();
                        case DOWNLOADED -> downloaded.incrementAndGet();
                        case SOFT_FAIL -> softFailures.incrementAndGet();
                        case HARD_FAIL -> hardFailures.incrementAndGet();
                    }
                    if (loaded.entry != null) {
                        synchronized (lock) {
                            next.put(ref.getPath(), loaded.entry);
                        }
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdownNow();
        }
        return new LoadBatch(next, downloaded.get(), reused.get(), softFailures.get(), hardFailures.get());
    }

    private OneLoad loadOne(ContentRef ref, ContentSnapshot previous) {
        Optional<ContentMeta> metaOpt;
        try {
            metaOpt = source.meta(ref);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "meta failed: path=" + ref.getPath(), e);
            return previous.get(ref.getPath())
                    .map(old -> new OneLoad(old, Kind.SOFT_FAIL))
                    .orElseGet(() -> new OneLoad(null, Kind.HARD_FAIL));
        }
        if (metaOpt.isEmpty()) {
            return previous.get(ref.getPath())
                    .map(old -> new OneLoad(old, Kind.SOFT_FAIL))
                    .orElseGet(() -> new OneLoad(null, Kind.HARD_FAIL));
        }
        ContentMeta meta = metaOpt.get();
        Optional<ContentEntry> old = previous.get(ref.getPath());
        if (old.isPresent() && old.get().getMeta().sameVersion(meta)) {
            return new OneLoad(old.get(), Kind.REUSED);
        }
        try {
            ContentEntry entry = materializer.materialize(ref.getPath(), meta, source.open(ref));
            return new OneLoad(entry, Kind.DOWNLOADED);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "download failed: path=" + ref.getPath(), e);
            return old.map(value -> new OneLoad(value, Kind.SOFT_FAIL))
                    .orElseGet(() -> new OneLoad(null, Kind.HARD_FAIL));
        }
    }

    private static boolean containsAllPaths(Map<String, ContentEntry> entries, List<String> paths) {
        for (String path : paths) {
            if (!entries.containsKey(path)) {
                return false;
            }
        }
        return true;
    }

    private static String joinPath(String prefix, String name) {
        if (prefix == null || prefix.isEmpty()) {
            return name;
        }
        if (prefix.endsWith("/")) {
            return prefix + name;
        }
        return prefix + "/" + name;
    }

    @Override
    public Optional<byte[]> get(String path) {
        return current().getBytes(path);
    }

    private void notifyViews(ContentSnapshot snap) {
        for (MemoryView<?> view : views) {
            view.onSnapshot(snap);
        }
    }

    private void notifyListeners(RefreshResult result) {
        for (RefreshListener listener : listeners) {
            try {
                listener.onRefresh(result);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "refresh listener failed", e);
            }
        }
    }

    private enum Kind {
        REUSED, DOWNLOADED, SOFT_FAIL, HARD_FAIL
    }

    private record OneLoad(ContentEntry entry, Kind kind) {
    }

    private static final class LoadBatch {
        private final Map<String, ContentEntry> entries;
        private final int downloaded;
        private final int reused;
        private final int softFailures;
        private final int hardFailures;

        private LoadBatch(
                Map<String, ContentEntry> entries,
                int downloaded,
                int reused,
                int softFailures,
                int hardFailures) {
            this.entries = entries;
            this.downloaded = downloaded;
            this.reused = reused;
            this.softFailures = softFailures;
            this.hardFailures = hardFailures;
        }
    }
}
