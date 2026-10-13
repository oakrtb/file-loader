package com.oakrtb.file.loader.store;

/**
 * Hook for refresh observability (metrics / logging / alerts).
 */
@FunctionalInterface
public interface RefreshListener {

    void onRefresh(RefreshResult result);

    static RefreshListener compose(RefreshListener... listeners) {
        return result -> {
            for (RefreshListener listener : listeners) {
                if (listener != null) {
                    listener.onRefresh(result);
                }
            }
        };
    }
}
