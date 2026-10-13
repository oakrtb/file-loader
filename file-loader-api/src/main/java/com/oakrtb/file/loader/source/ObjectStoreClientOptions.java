package com.oakrtb.file.loader.source;

/**
 * HTTP timeouts and retry policy for object storage clients.
 *
 * @param connectTimeoutMs connect timeout in milliseconds
 * @param socketTimeoutMs  read/socket timeout in milliseconds
 * @param maxAttempts      total attempts including the first try (&gt;= 1)
 * @param retryBackoffMs   base backoff between attempts
 */
public record ObjectStoreClientOptions(
        int connectTimeoutMs,
        int socketTimeoutMs,
        int maxAttempts,
        long retryBackoffMs
) {
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 60_000;
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_BACKOFF_MS = 200L;

    public ObjectStoreClientOptions {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectTimeoutMs must be > 0");
        }
        if (socketTimeoutMs <= 0) {
            throw new IllegalArgumentException("socketTimeoutMs must be > 0");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException("retryBackoffMs must be >= 0");
        }
    }

    public static ObjectStoreClientOptions defaults() {
        return new ObjectStoreClientOptions(
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_SOCKET_TIMEOUT_MS,
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_RETRY_BACKOFF_MS);
    }

    /** Extra retries after the first attempt (for SDK maxErrorRetry style APIs). */
    public int maxRetries() {
        return Math.max(0, maxAttempts - 1);
    }
}
