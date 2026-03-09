package com.vinekeepers.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal HTTP server for health checks (Prometheus blackbox / Grafana status).
 * Serves GET / and GET /health with 200 OK so probes report up.
 */
public final class HealthServer {

    private static final Logger log = LoggerFactory.getLogger(HealthServer.class);
    private static final byte[] OK = "OK\n".getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;

    public HealthServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 2);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "health-server");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.createContext("/health", this::handle);
    }

    public void start() {
        server.start();
        log.info("Health server listening on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        log.info("Health server stopped");
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, OK.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(OK);
        }
    }
}
