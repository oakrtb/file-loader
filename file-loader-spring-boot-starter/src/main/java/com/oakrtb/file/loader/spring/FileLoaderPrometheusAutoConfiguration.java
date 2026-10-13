package com.oakrtb.file.loader.spring;

import com.oakrtb.file.loader.metrics.PrometheusMetricsExporter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Optional dedicated Prometheus scrape port sharing Boot's {@link PrometheusMeterRegistry}.
 * Enable with {@code oakrtb.file-loader.prometheus.enabled=true}.
 */
@AutoConfiguration
@EnableConfigurationProperties(FileLoaderProperties.class)
@ConditionalOnClass(PrometheusMeterRegistry.class)
@ConditionalOnProperty(prefix = "oakrtb.file-loader.prometheus", name = "enabled", havingValue = "true")
public class FileLoaderPrometheusAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(PrometheusMeterRegistry.class)
    @ConditionalOnMissingBean(PrometheusMetricsExporter.class)
    public PrometheusMetricsExporter fileLoaderPrometheusMetricsExporter(
            PrometheusMeterRegistry registry,
            FileLoaderProperties props) {
        FileLoaderProperties.Prometheus cfg = props.getPrometheus();
        return PrometheusMetricsExporter.serve(
                registry,
                cfg.getHost() == null || cfg.getHost().isBlank() ? "0.0.0.0" : cfg.getHost(),
                cfg.getPort(),
                cfg.getPath());
    }
}
