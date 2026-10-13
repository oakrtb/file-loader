package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.GetObjectV2Output;
import com.volcengine.tos.model.object.HeadObjectV2Input;
import com.volcengine.tos.model.object.HeadObjectV2Output;
import com.volcengine.tos.model.object.ListObjectsType2Input;
import com.volcengine.tos.model.object.ListObjectsType2Output;
import com.volcengine.tos.model.object.ListedObjectV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TosContentSourceTest {

    @Mock
    TOSV2 client;

    @Test
    void listMetaAndOpenAgainstMockClient() throws Exception {
        ObjectStoreConfig config = ObjectStoreConfig.tos(
                "cn-beijing", "https://tos.example", "ak", "sk", "bucket",
                new ObjectStoreClientOptions(1000, 2000, 2, 10));

        ListedObjectV2 listed = new ListedObjectV2().setKey("conf/a.txt").setSize(5L);
        ListObjectsType2Output listing = new ListObjectsType2Output()
                .setContents(List.of(listed))
                .setTruncated(false);
        when(client.listObjectsType2(any(ListObjectsType2Input.class))).thenReturn(listing);

        HeadObjectV2Output head = mock(HeadObjectV2Output.class);
        when(head.getEtag()).thenReturn("etag-1");
        when(head.getLastModifiedInDate()).thenReturn(new Date(1_700_000_000_000L));
        when(head.getContentLength()).thenReturn(5L);
        when(client.headObject(any(HeadObjectV2Input.class))).thenReturn(head);

        GetObjectV2Output get = mock(GetObjectV2Output.class);
        when(get.getContent()).thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(client.getObject(any(GetObjectV2Input.class))).thenReturn(get);

        try (TosContentSource source = new TosContentSource(config, client)) {
            List<ContentRef> refs = source.list("conf/");
            assertEquals(1, refs.size());
            assertEquals("conf/a.txt", refs.get(0).getPath());

            Optional<ContentMeta> meta = source.meta(refs.get(0));
            assertTrue(meta.isPresent());
            assertEquals("etag-1", meta.get().getEtag());
            assertEquals(5, meta.get().getSize());

            try (var in = source.open(refs.get(0))) {
                assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        ArgumentCaptor<ListObjectsType2Input> captor = ArgumentCaptor.forClass(ListObjectsType2Input.class);
        verify(client).listObjectsType2(captor.capture());
        assertEquals("bucket", captor.getValue().getBucket());
        assertEquals("conf/", captor.getValue().getPrefix());
        verify(client, never()).close();
    }

    @Test
    void metaReturnsEmptyOn404() throws Exception {
        ObjectStoreConfig config = ObjectStoreConfig.tos(
                "cn-beijing", "https://tos.example", "ak", "sk", "bucket");
        when(client.headObject(any(HeadObjectV2Input.class)))
                .thenThrow(new TosServerException(404, "NoSuchKey", "missing", "req", "host"));

        try (TosContentSource source = new TosContentSource(config, client)) {
            assertTrue(source.meta(new ContentRef("missing.txt")).isEmpty());
        }
    }
}
