package com.oakrtb.file.loader.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oakrtb.file-loader")
public class FileLoaderProperties {

    /**
     * When false, auto-config does not create a FileLoader bean.
     */
    private boolean enabled = true;

    /**
     * local | oss | tos
     */
    private String source = "local";

    private String localRoot = ".";
    private String prefix = "";
    private String rollingDatePrefix;
    private String datePattern = "yyyy-MM-dd";
    private Duration refreshInterval;
    private String cacheDir;
    private boolean failFast = true;
    private boolean cacheFallback = false;
    private boolean watchLocalChanges = false;
    private boolean watchPollFallback = true;
    private boolean loggingListener = true;

    private String endpoint;
    private String accessKey;
    private String accessSecret;
    private String bucket;
    private String region;

    private long heapThresholdBytes = 4L * 1024 * 1024;
    private String spillDir;
    private int downloadConcurrency = 4;
    private boolean manifestEnabled = false;
    private String manifestName = "manifest.json";
    /**
     * When false, drop raw file bytes from the store after views parse (keep meta only).
     */
    private boolean retainRawContent = true;

    private int connectTimeoutMs = 10_000;
    private int socketTimeoutMs = 60_000;
    private int maxAttempts = 3;
    private long retryBackoffMs = 200;

    /**
     * Optional dedicated Prometheus scrape HTTP server (in addition to Actuator).
     * Prefer Actuator {@code /actuator/prometheus} in Spring Boot apps; enable this
     * when a classic exporter port (e.g. 9464) is required for VMP/Prometheus scrape.
     */
    private final Prometheus prometheus = new Prometheus();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getRollingDatePrefix() {
        return rollingDatePrefix;
    }

    public void setRollingDatePrefix(String rollingDatePrefix) {
        this.rollingDatePrefix = rollingDatePrefix;
    }

    public String getDatePattern() {
        return datePattern;
    }

    public void setDatePattern(String datePattern) {
        this.datePattern = datePattern;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isCacheFallback() {
        return cacheFallback;
    }

    public void setCacheFallback(boolean cacheFallback) {
        this.cacheFallback = cacheFallback;
    }

    public boolean isWatchLocalChanges() {
        return watchLocalChanges;
    }

    public void setWatchLocalChanges(boolean watchLocalChanges) {
        this.watchLocalChanges = watchLocalChanges;
    }

    public boolean isWatchPollFallback() {
        return watchPollFallback;
    }

    public void setWatchPollFallback(boolean watchPollFallback) {
        this.watchPollFallback = watchPollFallback;
    }

    public boolean isLoggingListener() {
        return loggingListener;
    }

    public void setLoggingListener(boolean loggingListener) {
        this.loggingListener = loggingListener;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String accessSecret) {
        this.accessSecret = accessSecret;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public long getHeapThresholdBytes() {
        return heapThresholdBytes;
    }

    public void setHeapThresholdBytes(long heapThresholdBytes) {
        this.heapThresholdBytes = heapThresholdBytes;
    }

    public String getSpillDir() {
        return spillDir;
    }

    public void setSpillDir(String spillDir) {
        this.spillDir = spillDir;
    }

    public int getDownloadConcurrency() {
        return downloadConcurrency;
    }

    public void setDownloadConcurrency(int downloadConcurrency) {
        this.downloadConcurrency = downloadConcurrency;
    }

    public boolean isManifestEnabled() {
        return manifestEnabled;
    }

    public void setManifestEnabled(boolean manifestEnabled) {
        this.manifestEnabled = manifestEnabled;
    }

    public String getManifestName() {
        return manifestName;
    }

    public void setManifestName(String manifestName) {
        this.manifestName = manifestName;
    }

    public boolean isRetainRawContent() {
        return retainRawContent;
    }

    public void setRetainRawContent(boolean retainRawContent) {
        this.retainRawContent = retainRawContent;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public static class Prometheus {
        /** When true, start an embedded scrape server on {@link #port}. */
        private boolean enabled = false;
        private String host = "0.0.0.0";
        private int port = 9464;
        private String path = "/metrics";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
