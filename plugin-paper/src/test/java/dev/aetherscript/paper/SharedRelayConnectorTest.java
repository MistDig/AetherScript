package dev.aetherscript.paper;
import dev.aetherscript.core.FeatureParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedRelayConnectorTest {
    @TempDir Path root;
    @Test void localMirrorPrecedesAckReconnectsAndCannotExpandTargets() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file);
        String enrollment = UUID.randomUUID().toString(), connection = UUID.randomUUID().toString();
        store.pending(enrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        var grant = Map.of("connectionId", connection, "enrollmentId", enrollment, "name", "Codex", "allowedScriptIds", List.of("hello-ai"), "scopes", SharedRelayConnector.SCOPES, "revoked", false);
        AtomicReference<Map<String, ?>> offered = new AtomicReference<>(grant);
        AtomicReference<String> requestedTarget = new AtomicReference<>("hello-ai");
        var calls = new ArrayList<String>(); AtomicInteger invoked = new AtomicInteger();
        SharedRelayConnector.Transport transport = (path, token, body) -> {
            calls.add(path);
            if (path.endsWith("/poll")) return FeatureParser.JSON.valueToTree(Map.of("grants", List.of(offered.get()), "requests", List.of(Map.of("connectionId", connection, "id", "request", "operation", "scripts.read", "arguments", Map.of("id", requestedTarget.get())))));
            if (path.endsWith("/ack")) assertTrue(new ConnectionStore(file).state().grants().containsKey(connection));
            return FeatureParser.JSON.valueToTree(Map.of("ok", true));
        };
        try (var connector = new SharedRelayConnector(store, (g, op, args) -> { invoked.incrementAndGet(); return Map.of("observed", true); }, g -> {}, line -> {}, transport)) { connector.pollOnce(); }
        assertEquals(1, invoked.get()); assertTrue(calls.stream().anyMatch(p -> p.endsWith("/ack")));
        var reopened = new ConnectionStore(file);
        try (var connector = new SharedRelayConnector(reopened, (g, op, args) -> { invoked.incrementAndGet(); return Map.of(); }, g -> {}, line -> {}, transport)) {
            connector.pollOnce(); assertEquals(2, invoked.get());
            var expanded = new HashMap<String, Object>(grant); expanded.put("allowedScriptIds", List.of("other")); offered.set(expanded);
            requestedTarget.set("other");
            connector.pollOnce(); assertEquals(Set.of("hello-ai"), reopened.state().grants().get(connection).allowedScriptIds());
            assertEquals(2, invoked.get(), "Changed relay target metadata cannot authorize a request");
        }
    }
    @Test void bareConnectNeedsNoRelayConfigAndDisconnectPersistsBeforeRelayNotification() throws Exception {
        var store = new ConnectionStore(root.resolve("connections.json"));
        String enrollment = UUID.randomUUID().toString(), connection = UUID.randomUUID().toString();
        CountDownLatch link = new CountDownLatch(1), revoked = new CountDownLatch(1);
        var transport = (SharedRelayConnector.Transport) (path, token, body) -> {
            if (path.equals("/v2/enrollments")) {
                assertNull(token); assertEquals(List.of(), body.get("allowedScriptIds")); assertEquals(store.state().serverToken(), body.get("serverToken"));
                assertEquals("Minecraft server", body.get("serverName"));
                return FeatureParser.JSON.valueToTree(Map.of("enrollmentId", enrollment, "browserUrl", SharedRelayConnector.ORIGIN + "/connect#code=private", "expiresAt", System.currentTimeMillis() + 600000));
            }
            if (path.endsWith("/revoke")) { assertTrue(new ConnectionStore(root.resolve("connections.json")).state().grants().get(connection).revoked()); throw new java.io.IOException("offline"); }
            return FeatureParser.JSON.valueToTree(Map.of());
        };
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> Map.of(), g -> {}, line -> { if (line.contains("private link")) link.countDown(); if (line.contains("revocation saved locally")) revoked.countDown(); }, transport)) {
            connector.enroll(Set.of()); assertTrue(link.await(5, TimeUnit.SECONDS)); assertTrue(store.state().pending().containsKey(enrollment));
            store.accept(new ConnectionStore.Grant(connection, "Codex", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false), enrollment);
            connector.disconnect(connection); assertTrue(connector.blocked(connection)); assertTrue(revoked.await(5, TimeUnit.SECONDS));
            assertTrue(new ConnectionStore(root.resolve("connections.json")).state().grants().get(connection).revoked());
        }
    }
    @Test void validationRepliesPreserveTheSourceLocationForTheAuthorizedClient() throws Exception {
        var store = new ConnectionStore(root.resolve("diagnostics.json"));
        String enrollment = UUID.randomUUID().toString(), connection = UUID.randomUUID().toString();
        store.pending(enrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        var grant = new ConnectionStore.Grant(connection, "Client", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false);
        store.accept(grant, enrollment);
        AtomicReference<Map<String, ?>> response = new AtomicReference<>();
        SharedRelayConnector.Transport transport = (path, token, body) -> {
            if (path.endsWith("/poll")) return FeatureParser.JSON.valueToTree(Map.of("grants", List.of(),
                "requests", List.of(Map.of("connectionId", connection, "id", "check",
                    "operation", "scripts.validate", "arguments", Map.of("id", "hello-ai", "source", "bad")))));
            if (path.endsWith("/respond")) response.set(body);
            return FeatureParser.JSON.valueToTree(Map.of());
        };
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> {
            throw new dev.aetherscript.core.ValidationException("$.source", "Expected text", 3, 7);
        }, g -> {}, line -> {}, transport)) {
            connector.pollOnce();
            var error = (Map<?, ?>) response.get().get("error");
            assertEquals("VALIDATION_FAILED", error.get("code"));
            assertTrue(error.get("message").toString().contains("line 3, column 7"));
        }
    }

}
