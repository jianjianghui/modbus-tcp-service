package com.example.modbus.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal HTTP server that exposes Micrometer metrics in Prometheus format.
 */
public final class PrometheusMetricsServer implements Closeable {

    private static final AtomicInteger THREAD_ID = new AtomicInteger();

    private final PrometheusMeterRegistry registry;
    private final HttpServer server;
    private final ExecutorService executor;
    private final boolean ownsRegistry;

    public PrometheusMetricsServer(int port) throws IOException {
        this(PrometheusConfig.DEFAULT, port);
    }

    public PrometheusMetricsServer(PrometheusConfig config, int port) throws IOException {
        this(new PrometheusMeterRegistry(Objects.requireNonNull(config, "config")), port, true);
    }

    public PrometheusMetricsServer(PrometheusMeterRegistry registry, int port) throws IOException {
        this(registry, port, false);
    }

    private PrometheusMetricsServer(PrometheusMeterRegistry registry, int port, boolean ownsRegistry) throws IOException {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ownsRegistry = ownsRegistry;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "prometheus-metrics-" + THREAD_ID.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        server.createContext("/metrics", this::handleMetricsRequest);
        server.setExecutor(executor);
        server.start();
    }

    private void handleMetricsRequest(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    public PrometheusMeterRegistry getRegistry() {
        return registry;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        if (ownsRegistry) {
            registry.close();
        }
    }
}
