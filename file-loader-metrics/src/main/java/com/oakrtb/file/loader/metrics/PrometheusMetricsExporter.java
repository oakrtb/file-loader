package com.oakrtb.file.loader.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embeddable Prometheus exporter: serves {@code GET /metrics} (configurable) from a
 * {@link PrometheusMeterRegistry} for Prometheus / VMP scrape.
 *
 * <p>Typical non-Spring usage:
 * <pre>{@code
 * try (PrometheusMetricsExporter exporter = PrometheusMetricsExporter.start(9464)) {
 *     FileLoader.builder()
 *         .addListener(new MicrometerRefreshListener(exporter.registry()))
 *         ...
 * }
 * }</pre>
 *
 * <p>Spring Boot apps can instead use Actuator {@code /actuator/prometheus} with
 * {@code micrometer-registry-prometheus}; use this class when you want a dedicated scrape port.
 */
public final class PrometheusMetricsExporter implements AutoCloseable {

    public static final int DEFAULT_PORT = 9464;
    public static final String DEFAULT_PATH = "/metrics";

    private final PrometheusMeterRegistry registry;
    private final HttpServer server;
    private final ExecutorService executor;
    private final boolean ownsRegistry;
    private final String path;
    private final int port;

    private PrometheusMetricsExporter(
            PrometheusMeterRegistry registry,
            boolean ownsRegistry,
            HttpServer server,
            ExecutorService executor,
            String path,
            int port) {
        this.registry = registry;
        this.ownsRegistry = ownsRegistry;
        this.server = server;
        this.executor = executor;
        this.path = path;
        this.port = port;
    }

    /**
     * Creates a new {@link PrometheusMeterRegistry} and serves it on {@code 0.0.0.0:port/metrics}.
     */
    public static PrometheusMetricsExporter start(int port) {
        return start("0.0.0.0", port, DEFAULT_PATH);
    }

    /**
     * Creates a new registry and HTTP scrape endpoint.
     */
    public static PrometheusMetricsExporter start(String host, int port, String path) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        return serve(registry, true, host, port, path);
    }

    /**
     * Serves an existing Prometheus registry (e.g. Spring Boot's) on a dedicated port.
     */
    public static PrometheusMetricsExporter serve(PrometheusMeterRegistry registry, int port) {
        return serve(registry, false, "0.0.0.0", port, DEFAULT_PATH);
    }

    /**
     * Serves an existing Prometheus registry on {@code host:port} at {@code path}.
     */
    public static PrometheusMetricsExporter serve(
            PrometheusMeterRegistry registry, String host, int port, String path) {
        return serve(registry, false, host, port, path);
    }

    private static PrometheusMetricsExporter serve(
            PrometheusMeterRegistry registry,
            boolean ownsRegistry,
            String host,
            int port,
            String path) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(host, "host");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        String normalizedPath = normalizePath(path);

        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            HttpServer server = HttpServer.create(address, 0);
            server.createContext(normalizedPath, exchange -> scrape(registry, exchange));
            // convenience: also answer / if path is /metrics
            if (!"/".equals(normalizedPath)) {
                server.createContext("/", exchange -> {
                    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())
                            && ("/".equals(exchange.getRequestURI().getPath())
                            || exchange.getRequestURI().getPath().isEmpty())) {
                        redirect(exchange, normalizedPath);
                    } else {
                        notFound(exchange);
                    }
                });
            }
            ExecutorService executor = Executors.newFixedThreadPool(2, new ExporterThreadFactory());
            server.setExecutor(executor);
            server.start();
            int boundPort = server.getAddress().getPort();
            return new PrometheusMetricsExporter(
                    registry, ownsRegistry, server, executor, normalizedPath, boundPort);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start Prometheus exporter on " + host + ":" + port + path, e);
        }
    }

    public MeterRegistry registry() {
        return registry;
    }

    public PrometheusMeterRegistry prometheusRegistry() {
        return registry;
    }

    public int port() {
        return port;
    }

    public String path() {
        return path;
    }

    public String scrapeUrl() {
        return "http://127.0.0.1:" + port + path;
    }

    /** Prometheus text exposition of current meters. */
    public String scrape() {
        return registry.scrape();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        if (ownsRegistry) {
            registry.close();
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_PATH;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static void scrape(PrometheusMeterRegistry registry, HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private static final class ExporterThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "file-loader-prometheus-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
