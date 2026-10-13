package com.oakrtb.file.loader.view;

import com.oakrtb.file.loader.store.ContentEntry;
import com.oakrtb.file.loader.store.ContentSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple view: path -> raw bytes.
 * Meta-only entries keep the previous bytes for that path.
 */
public final class BytesMapView implements MemoryView<Map<String, byte[]>> {

    private final AtomicReference<Map<String, byte[]>> view =
            new AtomicReference<>(Collections.emptyMap());

    @Override
    public Map<String, byte[]> get() {
        return view.get();
    }

    @Override
    public void onSnapshot(ContentSnapshot snapshot) {
        Map<String, byte[]> previous = view.get();
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (Map.Entry<String, ContentEntry> e : snapshot.getEntries().entrySet()) {
            String path = e.getKey();
            ContentEntry entry = e.getValue();
            if (!entry.hasContent()) {
                byte[] old = previous.get(path);
                if (old != null) {
                    map.put(path, old);
                }
                continue;
            }
            map.put(path, entry.getBytes());
        }
        view.set(Collections.unmodifiableMap(map));
    }
}
