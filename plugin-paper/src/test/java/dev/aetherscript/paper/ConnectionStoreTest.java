package dev.aetherscript.paper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionStoreTest {
    @TempDir Path root;
    String id() { return UUID.randomUUID().toString(); }
    @Test void identityGrantRestrictionsRevocationAndSingleUseSurviveRestart() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file);
        String enrollment = id(), connection = id();
        store.pending(enrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        var grant = new ConnectionStore.Grant(connection, "Codex", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false);
        store.accept(grant, enrollment);
        var reopened = new ConnectionStore(file);
        assertEquals(store.state(), reopened.state()); assertTrue(reopened.state().pending().isEmpty());
        assertThrows(java.io.IOException.class, () -> reopened.accept(new ConnectionStore.Grant(id(), "Other", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false), enrollment));
        assertThrows(java.io.IOException.class, () -> reopened.accept(new ConnectionStore.Grant(connection, "Codex", Set.of("other"), SharedRelayConnector.SCOPES, false), enrollment));
        reopened.revoke(connection);
        assertTrue(new ConnectionStore(file).state().grants().get(connection).revoked());
        assertTrue(reopened.accept(grant, enrollment).revoked(), "Relay replay cannot reinstate a locally revoked grant");
    }
    @Test void browserDelegationAlwaysProducesFiniteNonemptyTargets() throws Exception {
        var store = new ConnectionStore(root.resolve("connections.json")); String enrollment = id();
        store.pending(enrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        assertThrows(java.io.IOException.class, () -> store.accept(new ConnectionStore.Grant(id(), "bad", Set.of("other"), SharedRelayConnector.SCOPES, false), enrollment));
        assertThrows(java.io.IOException.class, () -> store.accept(new ConnectionStore.Grant(id(), "bad", Set.of("*"), SharedRelayConnector.SCOPES, false), enrollment));
        assertThrows(java.io.IOException.class, () -> store.accept(new ConnectionStore.Grant(id(), "bad", Set.of(), SharedRelayConnector.SCOPES, false), enrollment));
        String unrestricted = id(); store.pending(unrestricted, Set.of(), System.currentTimeMillis() + 60000);
        var accepted = store.accept(new ConnectionStore.Grant(id(), "Browser selected", Set.of("new-note"), Set.of("scripts.read"), false), unrestricted);
        assertEquals(Set.of("new-note"), accepted.allowedScriptIds());
        store.revokeAll(); assertTrue(store.state().pending().isEmpty());
    }
}
