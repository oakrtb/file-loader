package com.oakrtb.file.loader.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class ManifestParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public Manifest parse(byte[] bytes) throws IOException {
        Manifest manifest = mapper.readValue(bytes, Manifest.class);
        if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            throw new IOException("manifest.version is required");
        }
        if (manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
            throw new IOException("manifest.files must not be empty");
        }
        for (Manifest.ManifestFile file : manifest.getFiles()) {
            if (file.getPath() == null || file.getPath().isBlank()) {
                throw new IOException("manifest file path is required");
            }
        }
        return manifest;
    }
}
