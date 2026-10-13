package com.oakrtb.file.loader.store;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Resolves the load prefix on each refresh (supports fixed or rolling date folders).
 */
@FunctionalInterface
public interface PrefixResolver {

    String resolve();

    static PrefixResolver fixed(String prefix) {
        String normalized = normalizeBase(prefix);
        return () -> normalized;
    }

    /**
     * Example: base {@code task/} + pattern {@code yyyy-MM-dd} → {@code task/2026-10-12/}.
     */
    static PrefixResolver rollingDate(String basePrefix, String pattern) {
        return rollingDate(basePrefix, DateTimeFormatter.ofPattern(pattern), ZoneId.systemDefault());
    }

    static PrefixResolver rollingDate(String basePrefix, DateTimeFormatter formatter, ZoneId zoneId) {
        return rollingDate(basePrefix, formatter, zoneId, Clock.system(zoneId));
    }

    static PrefixResolver rollingDate(
            String basePrefix, DateTimeFormatter formatter, ZoneId zoneId, Clock clock) {
        Objects.requireNonNull(formatter, "formatter");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(clock, "clock");
        String base = normalizeBase(basePrefix);
        if (!base.isEmpty() && !base.endsWith("/")) {
            base = base + "/";
        }
        String finalBase = base;
        // Use explicit zoneId (not clock's zone) so callers can pair any Clock with a calendar zone.
        return () -> finalBase + formatter.format(LocalDate.ofInstant(clock.instant(), zoneId)) + "/";
    }

    /** Parent prefix to watch for rolling date layouts (without the date segment). */
    static String watchRootOf(String basePrefix) {
        return normalizeBase(basePrefix);
    }

    private static String normalizeBase(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String p = prefix.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
