package com.oakrtb.file.loader.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectStoreConfigTest {

    @Test
    void buildsOssAndTosConfigs() {
        ObjectStoreConfig oss = ObjectStoreConfig.oss("https://oss.example", "ak", "sk", "bucket");
        assertEquals("bucket", oss.bucket());
        assertNull(oss.region());

        ObjectStoreConfig tos = ObjectStoreConfig.tos("cn-beijing", "https://tos.example", "ak", "sk", "bucket");
        assertEquals("cn-beijing", tos.region());
    }

    @Test
    void tosSourceRequiresRegion() {
        ObjectStoreConfig missingRegion = ObjectStoreConfig.oss("https://tos.example", "ak", "sk", "bucket");
        assertThrows(IllegalArgumentException.class, () -> new TosContentSource(missingRegion));
    }
}
