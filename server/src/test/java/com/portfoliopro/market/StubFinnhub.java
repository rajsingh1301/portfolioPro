package com.portfoliopro.market;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for Finnhub on a local port. Tests assert against the number of requests
 * it received, which is the only way to prove the cache actually prevents upstream
 * calls rather than merely appearing to.
 */
public final class StubFinnhub {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    private final Map<String, String> bodyBySymbol = new ConcurrentHashMap<>();

    private volatile int status = 200;
    private volatile String body = "{}";

    private StubFinnhub(HttpServer server) {
        this.server = server;
    }

    public static StubFinnhub start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubFinnhub stub = new StubFinnhub(server);
        server.createContext("/", exchange -> {
            stub.requestCount.incrementAndGet();
            stub.paths.add(exchange.getRequestURI().toString());
            String query = exchange.getRequestURI().getQuery();
            String body = stub.body;
            if (query != null) {
                for (var entry : stub.bodyBySymbol.entrySet()) {
                    if (query.contains("symbol=" + entry.getKey())) {
                        body = entry.getValue();
                    }
                }
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return stub;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respondWith(int status, String body) {
        this.status = status;
        this.body = body;
    }

    /** Overrides the body for one symbol's requests; the status still applies to all. */
    public void respondForSymbol(String symbol, String body) {
        bodyBySymbol.put(symbol, body);
    }

    public int requestCount() {
        return requestCount.get();
    }

    public List<String> paths() {
        return List.copyOf(paths);
    }

    /** Zeroes the request counters but keeps the configured responses. */
    public void resetCounts() {
        requestCount.set(0);
        paths.clear();
    }

    public void reset() {
        requestCount.set(0);
        paths.clear();
        bodyBySymbol.clear();
        status = 200;
        body = "{}";
    }

    public void stop() {
        server.stop(0);
    }
}
