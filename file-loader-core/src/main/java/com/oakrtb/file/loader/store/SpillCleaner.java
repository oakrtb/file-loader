package com.oakrtb.file.loader.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Deletes orphaned spill files that are no longer referenced by the current snapshot.
 */
public final class SpillCleaner {

    private static final Logger LOG = Logger.getLogger(SpillCleaner.class.getName());

    private SpillCleaner() {
    }

    public static void cleanup(Path spillDir, ContentSnapshot snapshot) {
        if (spillDir == null || snapshot == null) {
            return;
        }
        Path spillRoot = spillDir.toAbsolutePath().normalize().resolve("spill");
        if (!Files.isDirectory(spillRoot)) {
            return;
        }
        Set<Path> live = new HashSet<>();
        for (ContentEntry entry : snapshot.getEntries().values()) {
            entry.getFile().ifPresent(file -> live.add(file.toAbsolutePath().normalize()));
        }
        try (Stream<Path> stream = Files.list(spillRoot)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                Path normalized = file.toAbsolutePath().normalize();
                if (!live.contains(normalized)) {
                    try {
                        Files.deleteIfExists(normalized);
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "failed to delete orphan spill: " + normalized, e);
                    }
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "spill cleanup failed under " + spillRoot, e);
        }
    }
}
