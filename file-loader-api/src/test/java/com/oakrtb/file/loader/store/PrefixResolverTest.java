package com.oakrtb.file.loader.store;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefixResolverTest {

    @Test
    void fixedNormalizesLeadingSlash() {
        assertEquals("conf/", PrefixResolver.fixed("/conf/").resolve());
        assertEquals("", PrefixResolver.fixed(null).resolve());
    }

    @Test
    void rollingDateAppendsTodaySegment() {
        ZoneId zone = ZoneId.of("UTC");
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), zone);
        PrefixResolver resolver = PrefixResolver.rollingDate(
                "task/", DateTimeFormatter.ofPattern("yyyy-MM-dd"), zone, clock);
        assertEquals("task/2026-10-12/", resolver.resolve());
        assertEquals("task/", PrefixResolver.watchRootOf("task/"));
    }

    @Test
    void rollingDateAddsTrailingSlashToBase() {
        ZoneId zone = ZoneId.of("UTC");
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), zone);
        PrefixResolver resolver = PrefixResolver.rollingDate(
                "data", DateTimeFormatter.ofPattern("yyyyMMdd"), zone, clock);
        assertEquals("data/20260102/", resolver.resolve());
    }

    @Test
    void rollingDateUsesExplicitZoneIdNotClockZone() {
        // Instant is still 2026-10-12 in UTC, but Asia/Shanghai is already 2026-10-13.
        Clock utcClock = Clock.fixed(Instant.parse("2026-10-12T16:30:00Z"), ZoneId.of("UTC"));
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        PrefixResolver resolver = PrefixResolver.rollingDate(
                "task/", DateTimeFormatter.ofPattern("yyyy-MM-dd"), shanghai, utcClock);
        assertEquals("task/2026-10-13/", resolver.resolve());
    }
}
