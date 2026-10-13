package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.spi.ObjectStoreSourceFactory;

public final class OssObjectStoreSourceFactory implements ObjectStoreSourceFactory {

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public ContentSource create(ObjectStoreConfig config) {
        return new OssContentSource(config);
    }
}
