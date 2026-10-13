package com.oakrtb.file.loader.source;

import com.oakrtb.file.loader.spi.ContentSource;
import com.oakrtb.file.loader.spi.ObjectStoreSourceFactory;

public final class TosObjectStoreSourceFactory implements ObjectStoreSourceFactory {

    @Override
    public String type() {
        return "tos";
    }

    @Override
    public ContentSource create(ObjectStoreConfig config) {
        return new TosContentSource(config);
    }
}
