package com.oakrtb.file.loader.view;

import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * View: matching path -> {@link Properties}. Default filter: {@code *.properties}.
 */
public final class PropertiesMapView extends AbstractParsingMapView<Properties> {

    public PropertiesMapView() {
        this(PathFilters.suffix(".properties"));
    }

    public PropertiesMapView(Predicate<String> pathFilter) {
        super(pathFilter);
    }

    @Override
    protected Properties parse(String path, byte[] bytes) throws Exception {
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(bytes));
        return props;
    }
}
