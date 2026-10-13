package com.oakrtb.file.loader.view;

import com.oakrtb.file.loader.store.ContentEntry;
import com.oakrtb.file.loader.store.ContentSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Map view that parses selected paths; on parse failure / meta-only entry keeps previous value.
 *
 * @param <V> parsed value type
 */
public abstract class AbstractParsingMapView<V> implements MemoryView<Map<String, V>> {

    private static final Logger LOG = Logger.getLogger(AbstractParsingMapView.class.getName());

    private final Predicate<String> pathFilter;
    private final AtomicReference<Map<String, V>> view = new AtomicReference<>(Collections.emptyMap());

    protected AbstractParsingMapView(Predicate<String> pathFilter) {
        this.pathFilter = Objects.requireNonNull(pathFilter, "pathFilter");
    }

    @Override
    public Map<String, V> get() {
        return view.get();
    }

    @Override
    public void onSnapshot(ContentSnapshot snapshot) {
        Map<String, V> previous = view.get();
        Map<String, V> next = new LinkedHashMap<>();
        for (Map.Entry<String, ContentEntry> e : snapshot.getEntries().entrySet()) {
            String path = e.getKey();
            if (!pathFilter.test(path)) {
                continue;
            }
            ContentEntry entry = e.getValue();
            if (!entry.hasContent()) {
                V old = previous.get(path);
                if (old != null) {
                    next.put(path, old);
                }
                continue;
            }
            try {
                next.put(path, parse(path, entry.getBytes()));
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "parse failed, keeping previous value if any: path=" + path, ex);
                V old = previous.get(path);
                if (old != null) {
                    next.put(path, old);
                }
            }
        }
        view.set(Collections.unmodifiableMap(next));
    }

    protected abstract V parse(String path, byte[] bytes) throws Exception;
}
