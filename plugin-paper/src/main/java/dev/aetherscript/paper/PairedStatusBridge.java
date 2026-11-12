package dev.aetherscript.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Loopback-only, ephemeral, read-only bridge. No Bukkit objects enter its worker threads. */
final class PairedStatusBridge implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService workers;
    private final Supplier<Map<String, Object>> status;
    private final Clock clock;
    private String code, token;
    private Instant codeExpiry = Instant.EPOCH, tokenExpiry = Instant.EPOCH;
    private int attempts, requests;
    private long window;
    private boolean closed;

    PairedStatusBridge(Supplier<Map<String, Object>> status) throws IOException { this(status, Clock.systemUTC()); }
    PairedStatusBridge(Supplier<Map<String, Object>> status, Clock clock) throws IOException {
        this.status = status; this.clock = clock;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8),
            r -> { Thread t = new Thread(r, "AetherScript-Status"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers); server.createContext("/", this::handle); server.start();
    }
    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    synchronized String pair() {
        if (closed) throw new IllegalStateException("Bridge closed");
        token = null; code = secret(); codeExpiry = clock.instant().plusSeconds(300); attempts = 0; return code;
    }
    private static String secret() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static boolean equal(String a, String b) { return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private record Response(int code, Map<String, ?> value) { }
    private static Response error(int code, String error) { return new Response(code, Map.of("error", error)); }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (host == null || !host.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) {
                send(exchange, 403, Map.of("error", "HOST_DENIED")); return;
            }
            if (exchange.getRequestHeaders().containsKey("Origin") || exchange.getRequestHeaders().containsKey("Sec-Fetch-Site")) {
                send(exchange, 403, Map.of("error", "BROWSER_DENIED")); return;
            }
            byte[] body = exchange.getRequestMethod().equals("POST") ? exchange.getRequestBody().readNBytes(1025) : new byte[0];
            String supplied = null;
            if (body.length <= 1024 && body.length > 0) {
                try {
                    var value = JSON.readTree(body);
                    if (value.isObject() && value.size() == 1 && value.has("code") && value.get("code").isTextual()) supplied = value.get("code").asText();
                } catch (IOException ignored) { }
            }
            Response response = process(exchange.getRequestURI(), exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst("Authorization"), supplied, body.length);
            send(exchange, response.code(), response.value());
        }
    }
    private synchronized Response process(URI uri, String method, String authorization, String supplied, int bodyLength) {
        long now = clock.millis() / 60000;
        if (now != window) { window = now; requests = 0; }
        if (closed) return error(503, "UNPAIRED");
        if (++requests > 120) return error(429, "RATE_LIMITED");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) return error(400, "INVALID_REQUEST");
        String path = uri.getRawPath();
        if (path.equals("/pair") && method.equals("POST")) {
            if (code == null || !clock.instant().isBefore(codeExpiry) || ++attempts > 8) {
                code = null; return error(401, "PAIRING_DENIED");
            }
            if (bodyLength > 1024) return error(413, "REQUEST_TOO_LARGE");
            if (!equal(code, supplied)) return error(401, "PAIRING_DENIED");
            code = null; token = secret(); tokenExpiry = clock.instant().plusSeconds(3600);
            return new Response(200, Map.of("token", token, "scope", "status", "expiresAt", tokenExpiry.toString()));
        }
        if (path.equals("/status") && method.equals("GET")) {
            if (token == null || !clock.instant().isBefore(tokenExpiry) || !equal("Bearer " + token, authorization)) return error(401, "UNAUTHORIZED");
            return new Response(200, status.get());
        }
        return error(404, "NOT_FOUND");
    }
    private static void send(HttpExchange exchange, int code, Map<String, ?> value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(code, body.length); exchange.getResponseBody().write(body);
    }
    @Override public void close() {
        // Stop sockets before taking the monitor: an incomplete request cannot block revocation.
        server.stop(0); workers.shutdownNow();
        synchronized (this) { closed = true; code = null; token = null; }
    }
}
