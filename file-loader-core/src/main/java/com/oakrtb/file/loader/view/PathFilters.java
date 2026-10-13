package com.oakrtb.file.loader.view;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Common path matchers for selecting files into a {@link MemoryView}.
 */
public final class PathFilters {

    private PathFilters() {
    }

    public static Predicate<String> suffix(String... suffixes) {
        Objects.requireNonNull(suffixes, "suffixes");
        String[] normalized = new String[suffixes.length];
        for (int i = 0; i < suffixes.length; i++) {
            normalized[i] = suffixes[i].toLowerCase(Locale.ROOT);
        }
        return path -> {
            if (path == null) {
                return false;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            for (String suffix : normalized) {
                if (lower.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        };
    }
}
