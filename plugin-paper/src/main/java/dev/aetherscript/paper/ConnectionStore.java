package dev.aetherscript.paper;

import dev.aetherscript.core.FeatureParser;
import dev.aetherscript.core.ScriptCapabilityPolicy;
import dev.aetherscript.core.StandardCallables;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/** Local identities and immutable grants survive restart; relay updates cannot enlarge them. */
final class ConnectionStore {
    static final Set<String> MESSAGE_ONLY = Set.of(ScriptCapabilityPolicy.MESSAGE);
    record Pending(String enrollmentId, Set<String> allowedScriptIds, long expiresAt, boolean allScripts,
                   Set<String> availableCapabilities, Set<String> testPlayerIds) {
        Pending(String enrollmentId, Set<String> allowedScriptIds, long expiresAt, boolean allScripts) { this(enrollmentId, allowedScriptIds, expiresAt, allScripts, MESSAGE_ONLY, Set.of()); }
        Pending(String enrollmentId, Set<String> allowedScriptIds, long expiresAt) { this(enrollmentId, allowedScriptIds, expiresAt, false); }
        Pending { allowedScriptIds = Set.copyOf(allowedScriptIds); availableCapabilities = availableCapabilities == null ? MESSAGE_ONLY : Set.copyOf(availableCapabilities); testPlayerIds = testPlayerIds == null ? Set.of() : Set.copyOf(testPlayerIds); }
    }
    record Grant(String connectionId, String name, Set<String> allowedScriptIds, Set<String> scopes, boolean revoked, boolean allScripts,
                 Set<String> capabilities, String testPlayerId) {
        Grant(String connectionId, String name, Set<String> allowedScriptIds, Set<String> scopes, boolean revoked, boolean allScripts) { this(connectionId, name, allowedScriptIds, scopes, revoked, allScripts, MESSAGE_ONLY, ""); }
        Grant(String connectionId, String name, Set<String> allowedScriptIds, Set<String> scopes, boolean revoked) { this(connectionId, name, allowedScriptIds, scopes, revoked, false); }
        Grant { allowedScriptIds = Set.copyOf(allowedScriptIds); scopes = Set.copyOf(scopes); capabilities = capabilities == null ? MESSAGE_ONLY : Set.copyOf(capabilities); testPlayerId = testPlayerId == null ? "" : testPlayerId; }
    }
    record State(String serverId, String serverToken, Map<String, Pending> pending, Map<String, Grant> grants) {
        State { pending = Map.copyOf(pending); grants = Map.copyOf(grants); }
    }
    private final Path file;
    private volatile State state;
    ConnectionStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (Files.exists(this.file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(this.file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe connection state");
            byte[] bytes;
            try (var input = Files.newInputStream(this.file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(262145); }
            if (bytes.length > 262144) throw new IOException("Connection state too large");
            state = FeatureParser.JSON.readValue(bytes, State.class); validate(state);
            expirePending(System.currentTimeMillis());
        } else {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            state = new State(UUID.randomUUID().toString(), HexFormat.of().formatHex(random), Map.of(), Map.of()); persist(state);
        }
    }
    // Immutable, published only after a durable save. Paper's effect guards must never wait
    // for the connection worker's disk I/O while reading the current authorization snapshot.
    State state() { return state; }
    synchronized void pending(String id, Set<String> targets, long expiresAt) throws IOException {
        pending(id, targets, expiresAt, false);
    }
    synchronized void pending(String id, Set<String> targets, long expiresAt, boolean allScripts) throws IOException {
        pending(id, targets, expiresAt, allScripts, MESSAGE_ONLY, Set.of());
    }
    synchronized void pending(String id, Set<String> targets, long expiresAt, boolean allScripts, Set<String> capabilities, Set<String> players) throws IOException {
        uuid(id); ids(targets, true);
        if (allScripts && !targets.isEmpty()) throw new IOException("Invalid enrollment access");
        save(new State(state.serverId(), state.serverToken(), Map.of(id, new Pending(id, Set.copyOf(targets), expiresAt, allScripts, capabilities, players)), state.grants()));
    }
    synchronized Map<String, Pending> expirePending(long now) throws IOException {
        var pending = new HashMap<>(state.pending()); var expired = new HashMap<String, Pending>();
        pending.forEach((id, value) -> { if (value.expiresAt() <= now) expired.put(id, value); });
        if (!expired.isEmpty()) { pending.keySet().removeAll(expired.keySet()); save(new State(state.serverId(), state.serverToken(), Map.copyOf(pending), state.grants())); }
        return Map.copyOf(expired);
    }
    synchronized void clearPending() throws IOException {
        if (!state.pending().isEmpty()) save(new State(state.serverId(), state.serverToken(), Map.of(), state.grants()));
    }
    synchronized void removePending(String id) throws IOException {
        if (state.pending().containsKey(id)) { var pending = new HashMap<>(state.pending()); pending.remove(id); save(new State(state.serverId(), state.serverToken(), Map.copyOf(pending), state.grants())); }
    }
    synchronized Grant accept(Grant offered, String enrollmentId) throws IOException {
        validateGrant(offered);
        Grant existing = state.grants().get(offered.connectionId());
        if (existing != null) {
            if (!existing.name().equals(offered.name()) || !existing.allowedScriptIds().equals(offered.allowedScriptIds()) || !existing.scopes().equals(offered.scopes()) || existing.allScripts() != offered.allScripts()
                || !existing.capabilities().equals(offered.capabilities()) || !existing.testPlayerId().equals(offered.testPlayerId())) throw new IOException("IMMUTABLE_GRANT_CHANGED");
            if (offered.revoked() && !existing.revoked()) { revoke(existing.connectionId()); return state.grants().get(existing.connectionId()); }
            return existing;
        }
        Pending pending = state.pending().get(enrollmentId);
        if (pending == null || pending.expiresAt() <= System.currentTimeMillis() || (offered.allScripts() && !pending.allScripts()) || (!pending.allowedScriptIds().isEmpty() && !pending.allowedScriptIds().containsAll(offered.allowedScriptIds()))
            || !pending.availableCapabilities().containsAll(offered.capabilities()) || (!offered.testPlayerId().isEmpty() && !pending.testPlayerIds().contains(offered.testPlayerId()))) throw new IOException("NO_LOCAL_ENROLLMENT");
        if (state.grants().size() >= 32) throw new IOException("Connection limit reached");
        var grants = new HashMap<>(state.grants()); grants.put(offered.connectionId(), offered);
        var nextPending = new HashMap<>(state.pending()); nextPending.remove(enrollmentId);
        save(new State(state.serverId(), state.serverToken(), Map.copyOf(nextPending), Map.copyOf(grants))); return offered;
    }
    synchronized void revoke(String id) throws IOException {
        var grants = new HashMap<>(state.grants());
        Grant old = grants.get(id);
        if (old != null) grants.put(id, new Grant(old.connectionId(), old.name(), old.allowedScriptIds(), old.scopes(), true, old.allScripts(), old.capabilities(), old.testPlayerId()));
        save(new State(state.serverId(), state.serverToken(), state.pending(), Map.copyOf(grants)));
    }
    synchronized void revokeAll() throws IOException {
        var grants = new HashMap<>(state.grants()); grants.replaceAll((id,g) -> new Grant(g.connectionId(), g.name(), g.allowedScriptIds(), g.scopes(), true, g.allScripts(), g.capabilities(), g.testPlayerId()));
        save(new State(state.serverId(), state.serverToken(), Map.of(), Map.copyOf(grants)));
    }
    private void save(State next) throws IOException { validate(next); persist(next); state = next; }
    private void persist(State next) throws IOException {
        Path parent = file.getParent();
        if (Files.isSymbolicLink(parent) || !parent.toRealPath().equals(parent) || Files.isSymbolicLink(file)) throw new IOException("Unsafe connection state path");
        byte[] bytes = FeatureParser.JSON.writeValueAsBytes(next);
        if (bytes.length > 262144) throw new IOException("Connection state too large");
        Path temporary = Files.createTempFile(parent, ".connections-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void validate(State value) throws IOException {
        if (value == null || value.serverToken() == null || !value.serverToken().matches("[0-9a-f]{64}") || value.grants() == null || value.pending() == null || value.grants().size() > 32 || value.pending().size() > 8) throw new IOException("Invalid connection state");
        uuid(value.serverId());
        for (var entry : value.grants().entrySet()) { validateGrant(entry.getValue()); if (!entry.getKey().equals(entry.getValue().connectionId())) throw new IOException("Grant ID mismatch"); }
        for (var entry : value.pending().entrySet()) {
            uuid(entry.getKey()); Pending pending = entry.getValue();
            if (!entry.getKey().equals(pending.enrollmentId())) throw new IOException("Enrollment ID mismatch"); ids(pending.allowedScriptIds(), true);
            if (pending.allScripts() && !pending.allowedScriptIds().isEmpty()) throw new IOException("Invalid enrollment access");
            capabilities(pending.availableCapabilities());
            if (pending.testPlayerIds().size() > 128) throw new IOException("Too many test players");
            for (String player : pending.testPlayerIds()) uuid(player);
        }
    }
    private static void validateGrant(Grant grant) throws IOException {
        if (grant == null || grant.name() == null || grant.name().isBlank() || grant.name().length() > 64 || grant.name().codePoints().anyMatch(Character::isISOControl) || grant.scopes() == null || !SharedRelayConnector.SCOPES.containsAll(grant.scopes()) || grant.scopes().isEmpty()) throw new IOException("Invalid grant");
        uuid(grant.connectionId()); ids(grant.allowedScriptIds(), grant.allScripts());
        if (grant.allScripts() && !grant.allowedScriptIds().isEmpty()) throw new IOException("Full grant must not include selected targets");
        capabilities(grant.capabilities()); if (!grant.testPlayerId().isEmpty()) uuid(grant.testPlayerId());
    }
    private static void capabilities(Set<String> values) throws IOException {
        Set<String> known = new HashSet<>(MESSAGE_ONLY);
        StandardCallables.create().descriptors().forEach(d -> known.addAll(d.requiredCapabilities()));
        if (values == null || !values.containsAll(MESSAGE_ONLY) || values.size() > 32 || !known.containsAll(values)) throw new IOException("Invalid execution capabilities");
    }
    private static void ids(Set<String> values, boolean empty) throws IOException {
        if (values == null || values.size() > 128 || (!empty && values.isEmpty())) throw new IOException("Invalid grant targets");
        try { values.forEach(FeatureParser::requireId); } catch (RuntimeException invalid) { throw new IOException("Invalid grant target"); }
    }
    private static void uuid(String value) throws IOException {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid connection ID"); }
    }
}
