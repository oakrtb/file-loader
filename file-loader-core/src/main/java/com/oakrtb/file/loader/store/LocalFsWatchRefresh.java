package com.oakrtb.file.loader.store;

import com.oakrtb.file.loader.source.LocalFsContentSource;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches a local directory tree and triggers {@link InMemoryStore#refresh()} on changes.
 * Events are debounced to avoid refresh storms during multi-file writes.
 */
public final class LocalFsWatchRefresh implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LocalFsWatchRefresh.class.getName());

    private final InMemoryStore store;
    private final Path watchRoot;
    private final Duration debounce;
    private final WatchService watchService;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pendingRefresh;
    private Thread watchThread;

    public LocalFsWatchRefresh(
            InMemoryStore store, LocalFsContentSource source, String prefix, Duration debounce) {
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(source, "source");
        this.debounce = Objects.requireNonNull(debounce, "debounce");
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("debounce must be >= 0");
        }
        Path resolved = source.resolvePrefix(prefix);
        this.watchRoot = Files.isDirectory(resolved) || !Files.exists(resolved)
                ? resolved
                : resolved.getParent() == null ? source.root() : resolved.getParent();
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new IllegalStateException("failed to create WatchService", e);
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("file-loader-watch-debounce"));
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Files.createDirectories(watchRoot);
        registerAll(watchRoot);
        watchThread = new Thread(this::loop, "file-loader-watch");
        watchThread.setDaemon(true);
        watchThread.start();
        LOG.info("local fs watch started root=" + watchRoot);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        started.set(false);
        ScheduledFuture<?> pending = pendingRefresh;
        if (pending != null) {
            pending.cancel(false);
        }
        scheduler.shutdownNow();
        try {
            watchService.close();
        } catch (IOException ignored) {
            // best-effort
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    private void loop() {
        while (!closed.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!closed.get()) {
                    LOG.log(Level.WARNING, "watch service stopped", e);
                }
                return;
            }
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    scheduleRefresh();
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path child = dir.resolve(ev.context());
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    try {
                        registerAll(child);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "failed to register new directory: " + child, e);
                    }
                }
                scheduleRefresh();
            }
            if (!key.reset()) {
                LOG.warning("watch key no longer valid: " + dir);
            }
        }
    }

    private void scheduleRefresh() {
        ScheduledFuture<?> previous = pendingRefresh;
        if (previous != null) {
            previous.cancel(false);
        }
        pendingRefresh = scheduler.schedule(this::safeRefresh, debounce.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void safeRefresh() {
        try {
            store.refresh();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "watch-triggered refresh failed", e);
        }
    }

    private void registerAll(Path start) throws IOException {
        if (!Files.exists(start)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
