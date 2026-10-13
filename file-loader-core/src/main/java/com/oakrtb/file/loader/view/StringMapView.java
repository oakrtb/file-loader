package com.oakrtb.file.loader.view;

import com.oakrtb.file.loader.store.ContentEntry;
import com.oakrtb.file.loader.store.ContentSnapshot;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * View: path -> UTF-8 (or custom charset) text content.
 * Meta-only entries keep the previous text for that path.
 */
public final class StringMapView implements MemoryView<Map<String, String>> {

    private final Charset charset;
    private final AtomicReference<Map<String, String>> view =
            new AtomicReference<>(Collections.emptyMap());

    public StringMapView() {
        this(StandardCharsets.UTF_8);
    }

    public StringMapView(Charset charset) {
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    @Override
    public Map<String, String> get() {
        return view.get();
    }

    @Override
    public void onSnapshot(ContentSnapshot snapshot) {
        Map<String, String> previous = view.get();
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, ContentEntry> e : snapshot.getEntries().entrySet()) {
            String path = e.getKey();
            ContentEntry entry = e.getValue();
            if (!entry.hasContent()) {
                String old = previous.get(path);
                if (old != null) {
                    map.put(path, old);
                }
                continue;
            }
            map.put(path, new String(entry.getBytes(), charset));
        }
        view.set(Collections.unmodifiableMap(map));
    }
}
