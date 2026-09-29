package com.portfoliopro.market;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
            byte[] payload = stub.body.getBytes(StandardCharsets.UTF_8);
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

    public int requestCount() {
        return requestCount.get();
    }

    public List<String> paths() {
        return List.copyOf(paths);
    }

    public void reset() {
        requestCount.set(0);
        paths.clear();
        status = 200;
        body = "{}";
    }

    public void stop() {
        server.stop(0);
    }
}
