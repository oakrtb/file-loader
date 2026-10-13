package com.oakrtb.file.loader.source;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.oakrtb.file.loader.spi.ContentMeta;
import com.oakrtb.file.loader.spi.ContentRef;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OssContentSourceTest {

    @Mock
    OSS client;

    @Test
    void listMetaAndOpenAgainstMockClient() throws Exception {
        ObjectStoreConfig config = ObjectStoreConfig.oss(
                "https://oss.example", "ak", "sk", "bucket",
                new ObjectStoreClientOptions(1000, 2000, 2, 10));

        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey("conf/a.txt");
        summary.setSize(5);
        ObjectListing listing = new ObjectListing();
        listing.setTruncated(false);
        listing.getObjectSummaries().add(summary);
        when(client.listObjects(any(ListObjectsRequest.class))).thenReturn(listing);

        when(client.doesObjectExist("bucket", "conf/a.txt")).thenReturn(true);
        ObjectMetadata metadata = org.mockito.Mockito.mock(ObjectMetadata.class);
        when(metadata.getETag()).thenReturn("\"etag-1\"");
        when(metadata.getLastModified()).thenReturn(new Date(1_700_000_000_000L));
        when(metadata.getContentLength()).thenReturn(5L);
        when(client.getObjectMetadata("bucket", "conf/a.txt")).thenReturn(metadata);

        OSSObject object = new OSSObject();
        object.setObjectContent(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(client.getObject("bucket", "conf/a.txt")).thenReturn(object);

        try (OssContentSource source = new OssContentSource(config, client)) {
            List<ContentRef> refs = source.list("conf/");
            assertEquals(1, refs.size());
            assertEquals("conf/a.txt", refs.get(0).getPath());

            Optional<ContentMeta> meta = source.meta(refs.get(0));
            assertTrue(meta.isPresent());
            assertEquals("\"etag-1\"", meta.get().getEtag());
            assertEquals(5, meta.get().getSize());

            try (var in = source.open(refs.get(0))) {
                assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        ArgumentCaptor<ListObjectsRequest> captor = ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(client).listObjects(captor.capture());
        assertEquals("bucket", captor.getValue().getBucketName());
        assertEquals("conf/", captor.getValue().getPrefix());
        verify(client).shutdown();
    }

    @Test
    void skipsDirectoryMarkersAndEmptyObjects() throws Exception {
        ObjectStoreConfig config = ObjectStoreConfig.oss("https://oss.example", "ak", "sk", "bucket");
        OSSObjectSummary dir = new OSSObjectSummary();
        dir.setKey("conf/");
        dir.setSize(0);
        OSSObjectSummary empty = new OSSObjectSummary();
        empty.setKey("conf/empty.txt");
        empty.setSize(0);
        ObjectListing listing = new ObjectListing();
        listing.setTruncated(false);
        listing.getObjectSummaries().add(dir);
        listing.getObjectSummaries().add(empty);
        when(client.listObjects(any(ListObjectsRequest.class))).thenReturn(listing);

        try (OssContentSource source = new OssContentSource(config, client)) {
            assertTrue(source.list("conf/").isEmpty());
        }
        verify(client).shutdown();
    }
}
