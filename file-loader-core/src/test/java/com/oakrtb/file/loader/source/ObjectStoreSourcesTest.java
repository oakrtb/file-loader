package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectStoreSourcesTest {

    @Test
    void wrapperOwnsAttemptsAndSdkSeesASingleTry() throws Exception {
        RecordingObjectStoreSourceFactory.received.set(null);
        RecordingObjectStoreSourceFactory.listCalls.set(0);
        RecordingObjectStoreSourceFactory.succeedOnAttempt = 3;

        ObjectStoreClientOptions caller = new ObjectStoreClientOptions(1000, 2000, 3, 0);
        ObjectStoreConfig config = new ObjectStoreConfig(
                "https://example", "ak", "sk", "bucket", "cn-test", caller);

        ContentSource source = ObjectStoreSources.create("recording", config);
        assertInstanceOf(RetryingContentSource.class, source);
        assertEquals(1, source.list("conf/").size());

        ObjectStoreConfig seen = RecordingObjectStoreSourceFactory.received.get();
        assertEquals(1, seen.clientOptions().maxAttempts());
        assertEquals(0, seen.clientOptions().maxRetries());
        assertEquals(1000, seen.clientOptions().connectTimeoutMs());
        assertEquals(2000, seen.clientOptions().socketTimeoutMs());
        assertEquals(0, seen.clientOptions().retryBackoffMs());
        assertEquals("cn-test", seen.region());
        assertEquals("bucket", seen.bucket());
        assertEquals(3, RecordingObjectStoreSourceFactory.listCalls.get());
    }

    @Test
    void singleAttemptConfigIsPassedThrough() {
        ObjectStoreClientOptions caller = new ObjectStoreClientOptions(1000, 2000, 1, 0);
        ObjectStoreConfig config = ObjectStoreConfig.oss(
                "https://example", "ak", "sk", "bucket", caller);
        assertSame(config, ObjectStoreSources.singleSdkAttempt(config));
    }
}
