package com.oakrtb.file.loader.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * View: matching path -> {@link JsonNode}. Default filter: {@code *.json}.
 */
public final class JsonNodeMapView extends AbstractParsingMapView<JsonNode> {

    private final ObjectMapper mapper;

    public JsonNodeMapView() {
        this(PathFilters.suffix(".json"), new ObjectMapper());
    }

    public JsonNodeMapView(Predicate<String> pathFilter) {
        this(pathFilter, new ObjectMapper());
    }

    public JsonNodeMapView(Predicate<String> pathFilter, ObjectMapper mapper) {
        super(pathFilter);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    protected JsonNode parse(String path, byte[] bytes) throws Exception {
        return mapper.readTree(bytes);
    }
}
