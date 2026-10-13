package com.oakrtb.file.loader.metrics;

import com.oakrtb.file.loader.store.RefreshResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsExporterTest {

    @Test
    void servesPrometheusTextAndRecordsRefreshMetrics() throws Exception {
        try (PrometheusMetricsExporter exporter = PrometheusMetricsExporter.start(0)) {
            MicrometerRefreshListener listener = new MicrometerRefreshListener(exporter.registry());
            listener.onRefresh(RefreshResult.ok(Duration.ofMillis(15), 2, 1, 1, 0, 2, Instant.now()));
            listener.onRefresh(RefreshResult.failed(Duration.ofMillis(4), new RuntimeException("boom")));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(exporter.scrapeUrl())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("file_loader_refresh_total"), body);
            assertTrue(body.contains("result=\"success\""), body);
            assertTrue(body.contains("result=\"failure\""), body);
            assertTrue(body.contains("file_loader_refresh_duration"), body);
        }
    }
}
