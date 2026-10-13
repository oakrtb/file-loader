package com.oakrtb.file.loader.view;

import com.oakrtb.file.loader.store.ContentSnapshot;

/**
 * Projects a {@link ContentSnapshot} into a domain-specific in-memory structure.
 *
 * @param <T> view type, e.g. Map, index, config object
 */
public interface MemoryView<T> {

    T get();

    /**
     * Rebuild this view from a new snapshot after store refresh.
     */
    void onSnapshot(ContentSnapshot snapshot);
}
