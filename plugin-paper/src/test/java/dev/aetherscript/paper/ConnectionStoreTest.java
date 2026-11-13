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
    @Test void fullAccessIsExplicitImmutableAndBackwardCompatible() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file);
        String enrollment = id(), connection = id();
        store.pending(enrollment, Set.of(), System.currentTimeMillis() + 60000, true);
        var full = new ConnectionStore.Grant(connection, "Full", Set.of(), SharedRelayConnector.SCOPES, false, true);
        store.accept(full, enrollment); assertTrue(new ConnectionStore(file).state().grants().get(connection).allScripts());
        assertThrows(java.io.IOException.class, () -> store.accept(new ConnectionStore.Grant(connection, "Full", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false, false), enrollment));
        store.revoke(connection); assertTrue(new ConnectionStore(file).state().grants().get(connection).allScripts());
        String oldEnrollment = id(), restricted = id();
        store.pending(oldEnrollment, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        store.accept(new ConnectionStore.Grant(restricted, "Old grant", Set.of("hello-ai"), SharedRelayConnector.SCOPES, false), oldEnrollment);
        Files.writeString(file, Files.readString(file).replace(",\"allScripts\":false", ""));
        var reopened = new ConnectionStore(file); assertFalse(reopened.state().grants().get(restricted).allScripts());
        assertThrows(java.io.IOException.class, () -> reopened.accept(new ConnectionStore.Grant(restricted, "Old grant", Set.of(), SharedRelayConnector.SCOPES, false, true), oldEnrollment));
        String restrictedLink = id(); reopened.pending(restrictedLink, Set.of("hello-ai"), System.currentTimeMillis() + 60000);
        assertThrows(java.io.IOException.class, () -> reopened.accept(new ConnectionStore.Grant(id(), "Escalation", Set.of(), SharedRelayConnector.SCOPES, false, true), restrictedLink));
    }
    @Test void pendingReplacementAndExpiryAreDurableWithoutRevokingApprovedApps() throws Exception {
        Path file = root.resolve("connections.json"); var store = new ConnectionStore(file); String first = id(), second = id();
        store.pending(first, Set.of(), System.currentTimeMillis() + 60000, true);
        store.pending(second, Set.of("note"), System.currentTimeMillis() + 60000);
        assertEquals(Set.of(second), new ConnectionStore(file).state().pending().keySet());
        store.expirePending(System.currentTimeMillis() + 120000);
        assertTrue(new ConnectionStore(file).state().pending().isEmpty());
        String oldBare = id(); store.pending(oldBare, Set.of(), System.currentTimeMillis() + 60000);
        assertThrows(java.io.IOException.class, () -> store.accept(new ConnectionStore.Grant(id(), "Old bare link", Set.of(), SharedRelayConnector.SCOPES, false, true), oldBare));
    }
}
