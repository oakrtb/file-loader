package com.oakrtb.file.loader.store;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured-ish JUL logger for refresh results.
 */
public final class LoggingRefreshListener implements RefreshListener {

    private static final Logger LOG = Logger.getLogger(LoggingRefreshListener.class.getName());

    @Override
    public void onRefresh(RefreshResult result) {
        if (result.isSkipped()) {
            LOG.info("file-loader refresh skipped overlapping run durationMs=" + result.getDuration().toMillis());
            return;
        }
        if (result.isSuccess()) {
            LOG.info("file-loader refresh ok"
                    + " durationMs=" + result.getDuration().toMillis()
                    + " listed=" + result.getListed()
                    + " downloaded=" + result.getDownloaded()
                    + " reused=" + result.getReused()
                    + " failedFiles=" + result.getFailedFiles()
                    + " entryCount=" + result.getEntryCount()
                    + " loadedAt=" + result.getLoadedAt().orElse(null));
            return;
        }
        LOG.log(Level.WARNING, "file-loader refresh failed durationMs=" + result.getDuration().toMillis()
                + " error=" + result.getError().map(Throwable::toString).orElse("unknown"),
                result.getError().orElse(null));
    }
}
