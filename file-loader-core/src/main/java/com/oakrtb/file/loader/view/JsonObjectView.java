package com.oakrtb.file.loader.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oakrtb.file.loader.store.ContentEntry;
import com.oakrtb.file.loader.store.ContentSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * View: single JSON file path -> typed object (with source path).
 * Parse failure / meta-only entry keeps previous value.
 *
 * @param <T> target type
 */
public final class JsonObjectView<T> implements MemoryView<Optional<SourcedValue<T>>> {

    private static final Logger LOG = Logger.getLogger(JsonObjectView.class.getName());

    private final String path;
    private final Class<T> type;
    private final ObjectMapper mapper;
    private final AtomicReference<Optional<SourcedValue<T>>> view =
            new AtomicReference<>(Optional.empty());

    public JsonObjectView(String path, Class<T> type) {
        this(path, type, new ObjectMapper());
    }

    public JsonObjectView(String path, Class<T> type, ObjectMapper mapper) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Logical path this view is bound to. */
    public String path() {
        return path;
    }

    @Override
    public Optional<SourcedValue<T>> get() {
        return view.get();
    }

    @Override
    public void onSnapshot(ContentSnapshot snapshot) {
        ContentEntry entry = snapshot.getEntries().get(path);
        if (entry == null) {
            view.set(Optional.empty());
            return;
        }
        if (!entry.hasContent()) {
            // raw discarded after previous parse; keep business object
            return;
        }
        try {
            T value = mapper.readValue(entry.getBytes(), type);
            view.set(Optional.of(new SourcedValue<>(path, value)));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "json object parse failed, keeping previous: path=" + path, e);
        }
    }
}
