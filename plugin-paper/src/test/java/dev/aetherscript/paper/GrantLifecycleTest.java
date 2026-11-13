package dev.aetherscript.paper;
import dev.aetherscript.core.FeatureParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GrantLifecycleTest {
    @TempDir Path root;
    String uuid() { return UUID.randomUUID().toString(); }
    @Test void staleWarningsAndTerminalRejectsAreDeduplicatedPerStateAndExpiryPersists() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file);
        String initial = uuid(), active = uuid(); store.pending(initial, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        store.accept(new ConnectionStore.Grant(active, "Existing", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false), initial);
        String expired = uuid(), unknown = uuid(); store.pending(expired, Set.of(), System.currentTimeMillis() - 1, true);
        AtomicReference<Map<String, ?>> offered = new AtomicReference<>(Map.of("connectionId", unknown, "enrollmentId", expired, "name", "Stale", "allowedScriptIds", List.of("hello-ai"), "scopes", SharedRelayConnector.SCOPES, "revoked", false));
        AtomicInteger rejects = new AtomicInteger(); List<String> lines = new ArrayList<>();
        SharedRelayConnector.Transport transport = (path, token, body) -> {
            if (path.endsWith("/poll")) return FeatureParser.JSON.valueToTree(Map.of("grants", List.of(offered.get()), "requests", List.of()));
            if (path.endsWith("/reject")) { rejects.incrementAndGet(); assertEquals("enrollment_expired", body.get("reason")); }
            return FeatureParser.JSON.valueToTree(Map.of("ok", true));
        };
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> Map.of(), g -> {}, lines::add, transport)) {
            for (int i = 0; i < 10; i++) connector.pollOnce();
            assertEquals(1, lines.stream().filter(line -> line.startsWith("Rejected connection offer")).count()); assertEquals(1, rejects.get());
            assertTrue(new ConnectionStore(file).state().pending().isEmpty());
            var changed = new HashMap<String, Object>(offered.get()); changed.put("name", "Changed offer"); offered.set(changed);
            connector.pollOnce(); connector.pollOnce(); assertEquals(2, rejects.get());
            assertEquals(2, lines.stream().filter(line -> line.startsWith("Rejected connection offer")).count());
        }
    }
    @Test void freshConnectClearsOldPendingBeforeNetworkAndFullGrantPermitsNewIds() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file);
        String old = uuid(), fresh = uuid(), connection = uuid(); store.pending(old, Set.of("old-note"), System.currentTimeMillis() + 60000);
        CountDownLatch enrolled = new CountDownLatch(1); AtomicInteger invoked = new AtomicInteger();
        var offered = Map.of("connectionId", connection, "enrollmentId", fresh, "name", "Full", "allowedScriptIds", List.of(), "allScripts", true, "scopes", SharedRelayConnector.SCOPES, "revoked", false);
        SharedRelayConnector.Transport transport = (path, token, body) -> {
            if (path.equals("/v2/enrollments")) {
                assertTrue(new ConnectionStore(file).state().pending().isEmpty()); assertEquals(true, body.get("allScripts"));
                return FeatureParser.JSON.valueToTree(Map.of("enrollmentId", fresh, "browserUrl", SharedRelayConnector.ORIGIN + "/connect#code=private", "expiresAt", System.currentTimeMillis() + 600000));
            }
            if (path.endsWith("/poll")) return FeatureParser.JSON.valueToTree(Map.of("grants", List.of(offered), "requests", List.of(Map.of("connectionId", connection, "id", "new-request", "operation", "scripts.read", "arguments", Map.of("id", "never-seen-before")))));
            return FeatureParser.JSON.valueToTree(Map.of("ok", true));
        };
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> { assertTrue(g.allScripts()); invoked.incrementAndGet(); return Map.of(); }, g -> {}, line -> { if (line.contains("Open this private link")) enrolled.countDown(); }, transport)) {
            connector.enroll(Set.of()); assertTrue(enrolled.await(5, TimeUnit.SECONDS));
            assertEquals(Set.of(fresh), new ConnectionStore(file).state().pending().keySet());
            connector.pollOnce(); assertEquals(1, invoked.get()); assertTrue(new ConnectionStore(file).state().grants().get(connection).allScripts());
        }
    }
    @Test void strippedRevokedOfferRevokesOriginalGrantAndServiceExactlyOnce() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file); String enrollment = uuid(), connection = uuid();
        store.pending(enrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        store.accept(new ConnectionStore.Grant(connection, "Original", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false), enrollment);
        AtomicInteger callbacks = new AtomicInteger(); List<String> lines = new ArrayList<>();
        var revoked = Map.of("connectionId", connection, "enrollmentId", enrollment, "revoked", true, "allowedScriptIds", List.of(), "scopes", List.of());
        SharedRelayConnector.Transport transport = (path, token, body) -> FeatureParser.JSON.valueToTree(Map.of("grants", List.of(revoked), "requests", List.of()));
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> fail("revoked request invoked"), g -> { assertTrue(g.revoked()); assertEquals(Set.of("hello-ai"), g.allowedScriptIds()); callbacks.incrementAndGet(); }, lines::add, transport)) {
            for (int i = 0; i < 10; i++) connector.pollOnce();
            assertEquals(1, callbacks.get()); assertTrue(connector.blocked(connection)); assertTrue(new ConnectionStore(file).state().grants().get(connection).revoked());
            assertTrue(lines.isEmpty());
        }
    }
    @Test void unknownOfferRejectNetworkFailuresDoNotProduceRepeatedGrantWarnings() throws Exception {
        var store = new ConnectionStore(root.resolve("connections.json")); String pending = uuid(); store.pending(pending, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        var offered = Map.of("connectionId", uuid(), "enrollmentId", uuid(), "name", "Unknown", "allowedScriptIds", List.of("hello-ai"), "scopes", SharedRelayConnector.SCOPES, "revoked", false);
        List<String> lines = new ArrayList<>(); AtomicInteger attempts = new AtomicInteger();
        SharedRelayConnector.Transport transport = (path, token, body) -> {
            if (path.endsWith("/reject")) { attempts.incrementAndGet(); throw new java.io.IOException("offline"); }
            return FeatureParser.JSON.valueToTree(Map.of("grants", List.of(offered), "requests", List.of()));
        };
        try (var connector = new SharedRelayConnector(store, (g,o,a) -> Map.of(), g -> {}, lines::add, transport)) {
            for (int i = 0; i < 5; i++) connector.pollOnce();
            assertEquals(1, lines.size()); assertEquals(5, attempts.get());
        }
    }
}
