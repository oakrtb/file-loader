package com.oakrtb.file.loader.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * Multi-file consistency document.
 *
 * <pre>
 * {
 *   "version": "20260101-1",
 *   "files": [ { "path": "conf/a.json" } ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Manifest {

    private String version;
    private List<ManifestFile> files = List.of();

    public Manifest() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ManifestFile> getFiles() {
        return files == null ? List.of() : files;
    }

    public void setFiles(List<ManifestFile> files) {
        this.files = files == null ? List.of() : List.copyOf(files);
    }

    public List<String> paths() {
        return getFiles().stream().map(ManifestFile::getPath).filter(Objects::nonNull).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ManifestFile {
        private String path;

        public ManifestFile() {
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
