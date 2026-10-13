package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentRef;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional real-bucket smoke test. Skipped unless {@code FILE_LOADER_IT=true}.
 *
 * <pre>
 * FILE_LOADER_IT=true FILE_LOADER_SOURCE=tos \
 *   FILE_LOADER_REGION=... FILE_LOADER_ENDPOINT=... \
 *   FILE_LOADER_ACCESS_KEY=... FILE_LOADER_ACCESS_SECRET=... \
 *   FILE_LOADER_BUCKET=... FILE_LOADER_PREFIX=conf/ \
 *   mvn -pl file-loader-tos test -Dtest=TosLiveIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "FILE_LOADER_IT", matches = "true")
class TosLiveIT {

    @Test
    void listPrefixAgainstRealBucket() throws Exception {
        Assumptions.assumeTrue("tos".equalsIgnoreCase(env("FILE_LOADER_SOURCE", "tos")));
        ObjectStoreConfig config = ObjectStoreConfig.tos(
                required("FILE_LOADER_REGION"),
                required("FILE_LOADER_ENDPOINT"),
                required("FILE_LOADER_ACCESS_KEY"),
                required("FILE_LOADER_ACCESS_SECRET"),
                required("FILE_LOADER_BUCKET"),
                new ObjectStoreClientOptions(5_000, 30_000, 2, 200));
        String prefix = env("FILE_LOADER_PREFIX", "");
        try (TosContentSource source = new TosContentSource(config)) {
            List<ContentRef> refs = source.list(prefix);
            assertNotNull(refs);
            assertFalse(refs.isEmpty(), "expected at least one object under prefix=" + prefix);
            ContentRef first = refs.get(0);
            assertTrue(source.meta(first).isPresent());
            try (var in = source.open(first)) {
                assertNotNull(in.readAllBytes());
            }
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        Assumptions.assumeTrue(value != null && !value.isBlank(), "missing " + key);
        return value;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
