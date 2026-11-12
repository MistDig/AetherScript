package dev.aetherscript.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PairedStatusBridgeTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private HttpResponse<String> request(PairedStatusBridge bridge, String path, String method, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(bridge.endpoint() + path)).timeout(Duration.ofSeconds(3));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String pair(PairedStatusBridge bridge, String code) throws Exception {
        var response = request(bridge, "/pair", "POST", "{\"code\":\"" + code + "\"}", null);
        assertEquals(200, response.statusCode());
        var json = new ObjectMapper().readTree(response.body());
        assertEquals("status", json.get("scope").asText());
        assertTrue(json.get("token").asText().matches("[A-Za-z0-9_-]{43}"));
        return json.get("token").asText();
    }
    @Test void oneTimePairRevocationReadOnlyAndNoBrowser() throws Exception {
        try (var bridge = new PairedStatusBridge(() -> Map.of("generation", 4))) {
            assertTrue(bridge.endpoint().startsWith("http://127.0.0.1:"));
            assertEquals(401, request(bridge, "/status", "GET", "", null).statusCode());
            String code = bridge.pair();
            assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
            String token = pair(bridge, code);
            assertEquals(401, request(bridge, "/pair", "POST", "{\"code\":\"" + code + "\"}", null).statusCode());
            assertEquals("{\"generation\":4}", request(bridge, "/status", "GET", "", token).body());
            assertEquals(404, request(bridge, "/reload", "POST", "", token).statusCode());
            assertEquals(404, request(bridge, "/status", "POST", "", token).statusCode());
            assertEquals(400, request(bridge, "/status?token=x", "GET", "", token).statusCode());
            var browser = HttpRequest.newBuilder(URI.create(bridge.endpoint() + "/status")).header("Origin", "http://evil.example").header("Authorization", "Bearer " + token).build();
            assertEquals(403, client.send(browser, HttpResponse.BodyHandlers.ofString()).statusCode());
            String replacement = bridge.pair();
            assertEquals(401, request(bridge, "/status", "GET", "", token).statusCode());
            pair(bridge, replacement);
        }
    }
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Test void expirationAndAttemptLimits() throws Exception {
        var clock = new MutableClock();
        try (var bridge = new PairedStatusBridge(Map::of, clock)) {
            String code = bridge.pair(); clock.now = clock.now.plusSeconds(300);
            assertEquals(401, request(bridge, "/pair", "POST", "{\"code\":\"" + code + "\"}", null).statusCode());
            String token = pair(bridge, bridge.pair()); clock.now = clock.now.plusSeconds(3600);
            assertEquals(401, request(bridge, "/status", "GET", "", token).statusCode());
            code = bridge.pair();
            for (int i = 0; i < 8; i++) assertEquals(401, request(bridge, "/pair", "POST", "{\"code\":\"wrong\"}", null).statusCode());
            assertEquals(401, request(bridge, "/pair", "POST", "{\"code\":\"" + code + "\"}", null).statusCode());
        }
    }
    @Test void slowBodyCannotBlockConsoleRePairAndSnapshotsStayFresh() throws Exception {
        var generation = new java.util.concurrent.atomic.AtomicInteger(1);
        try (var bridge = new PairedStatusBridge(() -> Map.of("generation", generation.get()))) {
            String token = pair(bridge, bridge.pair());
            generation.set(2);
            assertEquals("{\"generation\":2}", request(bridge, "/status", "GET", "", token).body());
            try (var socket = new java.net.Socket("127.0.0.1", URI.create(bridge.endpoint()).getPort())) {
                socket.getOutputStream().write(("POST /pair HTTP/1.1\r\nHost: " + URI.create(bridge.endpoint()).getAuthority()
                    + "\r\nContent-Length: 100\r\n\r\n{").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTimeoutPreemptively(Duration.ofSeconds(1), bridge::pair);
            }
        }
    }
    @Test void boundedBodyAndRequests() throws Exception {
        try (var bridge = new PairedStatusBridge(Map::of, new MutableClock())) {
            bridge.pair();
            assertEquals(413, request(bridge, "/pair", "POST", "x".repeat(1025), null).statusCode());
            for (int i = 0; i < 119; i++) request(bridge, "/unknown", "GET", "", null);
            assertEquals(429, request(bridge, "/status", "GET", "", null).statusCode());
        }
    }
}
