package com.oakrtb.file.loader.spring;

import com.oakrtb.file.loader.FileLoader;
import com.oakrtb.file.loader.metrics.MicrometerRefreshListener;
import com.oakrtb.file.loader.source.ObjectStoreClientOptions;
import com.oakrtb.file.loader.source.ObjectStoreConfig;
import com.oakrtb.file.loader.store.StoreOptions;
import com.oakrtb.file.loader.view.JsonNodeMapView;
import com.oakrtb.file.loader.view.StringMapView;
import com.oakrtb.file.loader.view.ViewRegistration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;

@AutoConfiguration
@EnableConfigurationProperties(FileLoaderProperties.class)
@ConditionalOnProperty(prefix = "oakrtb.file-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileLoaderAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public FileLoader fileLoader(
            FileLoaderProperties props,
            ObjectProvider<MeterRegistry> meterRegistries,
            ObjectProvider<ViewRegistration> viewRegistrations)
            throws Exception {
        StoreOptions.Builder store = StoreOptions.builder()
                .heapThresholdBytes(props.getHeapThresholdBytes())
                .downloadConcurrency(props.getDownloadConcurrency())
                .manifestEnabled(props.isManifestEnabled())
                .manifestName(props.getManifestName())
                .retainRawContent(props.isRetainRawContent());
        if (props.getSpillDir() != null && !props.getSpillDir().isBlank()) {
            store.spillDir(Path.of(props.getSpillDir()));
        }

        ObjectStoreClientOptions clientOptions = new ObjectStoreClientOptions(
                props.getConnectTimeoutMs(),
                props.getSocketTimeoutMs(),
                props.getMaxAttempts(),
                props.getRetryBackoffMs());

        FileLoader.Builder builder = FileLoader.builder()
                .storeOptions(store.build())
                .failFast(props.isFailFast())
                .cacheFallback(props.isCacheFallback())
                .watchLocalChanges(props.isWatchLocalChanges())
                .watchPollFallback(props.isWatchPollFallback())
                .loggingListener(props.isLoggingListener())
                .addView("text", new StringMapView())
                .addView("json", new JsonNodeMapView());

        viewRegistrations.orderedStream().forEach(builder::addView);

        if (props.getRollingDatePrefix() != null && !props.getRollingDatePrefix().isBlank()) {
            builder.rollingDatePrefix(props.getRollingDatePrefix(), props.getDatePattern());
        } else {
            builder.prefix(props.getPrefix() == null ? "" : props.getPrefix());
        }
        if (props.getRefreshInterval() != null) {
            builder.refreshEvery(props.getRefreshInterval());
        }
        if (props.getCacheDir() != null && !props.getCacheDir().isBlank()) {
            builder.cacheDir(Path.of(props.getCacheDir()));
        }

        String source = props.getSource() == null ? "local" : props.getSource().trim().toLowerCase();
        switch (source) {
            case "oss" -> builder.oss(ObjectStoreConfig.oss(
                    require(props.getEndpoint(), "endpoint"),
                    require(props.getAccessKey(), "accessKey"),
                    require(props.getAccessSecret(), "accessSecret"),
                    require(props.getBucket(), "bucket"),
                    clientOptions));
            case "tos" -> builder.tos(ObjectStoreConfig.tos(
                    require(props.getRegion(), "region"),
                    require(props.getEndpoint(), "endpoint"),
                    require(props.getAccessKey(), "accessKey"),
                    require(props.getAccessSecret(), "accessSecret"),
                    require(props.getBucket(), "bucket"),
                    clientOptions));
            case "local" -> builder.local(resolveLocalRoot(props.getLocalRoot()));
            default -> throw new IllegalArgumentException("unsupported oakrtb.file-loader.source: " + source);
        }

        MeterRegistry registry = meterRegistries.getIfAvailable();
        if (registry != null) {
            builder.addListener(new MicrometerRefreshListener(registry));
        }

        FileLoader loader = builder.build();
        loader.start();
        return loader;
    }

    private static Path resolveLocalRoot(String localRoot) {
        Path configured = Path.of(localRoot == null || localRoot.isBlank() ? "." : localRoot);
        if (configured.isAbsolute()) {
            return configured;
        }
        Path fromCwd = Path.of(System.getProperty("user.dir")).resolve(configured).normalize();
        if (Files.isDirectory(fromCwd)) {
            return fromCwd;
        }
        return configured;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("oakrtb.file-loader." + name + " is required");
        }
        return value;
    }
}
