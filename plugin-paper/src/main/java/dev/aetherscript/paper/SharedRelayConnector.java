package dev.aetherscript.paper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Shared-service browser enrollment. Secrets stay in the local server identity file. */
final class SharedRelayConnector implements AutoCloseable {
    static final URI ORIGIN = URI.create("https://aetherscript-relay.mistdig143.workers.dev");
    static final Set<String> SCOPES = Set.of("status", "scripts.list", "scripts.read", "scripts.validate", "scripts.apply", "scripts.test");
    interface Handler { Map<String, Object> execute(ConnectionStore.Grant grant, String operation, Map<String, Object> arguments) throws Exception; }
    interface Transport { JsonNode post(String path, String token, Map<String, ?> body) throws Exception; }
    private final ConnectionStore store;
    private final Handler handler;
    private final Consumer<ConnectionStore.Grant> grantChanged;
    private final Consumer<String> notice;
    private final Transport transport;
    private final String serverName;
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final Map<String, String> enrollmentOutcomes = boundedMap(), grantStates = boundedMap(), terminalStates = boundedMap(), acknowledged = boundedMap();
    private final AtomicLong enrollmentEpoch = new AtomicLong();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r, "AetherScript-Connections"); t.setDaemon(true); return t; });
    private volatile boolean closed;
    private boolean offlineNotice;
    SharedRelayConnector(ConnectionStore store, Handler handler, Consumer<ConnectionStore.Grant> grantChanged, Consumer<String> notice) {
        this(store, handler, grantChanged, notice, httpTransport(), "Minecraft server");
    }
    SharedRelayConnector(ConnectionStore store, Handler handler, Consumer<ConnectionStore.Grant> grantChanged, Consumer<String> notice, String serverName) {
        this(store, handler, grantChanged, notice, httpTransport(), serverName);
    }
    SharedRelayConnector(ConnectionStore store, Handler handler, Consumer<ConnectionStore.Grant> grantChanged, Consumer<String> notice, Transport transport) {
        this(store, handler, grantChanged, notice, transport, "Minecraft server");
    }
    private SharedRelayConnector(ConnectionStore store, Handler handler, Consumer<ConnectionStore.Grant> grantChanged, Consumer<String> notice, Transport transport, String serverName) {
        this.store = store; this.handler = handler; this.grantChanged = grantChanged; this.notice = notice; this.transport = transport;
        String display = serverName == null ? "" : serverName.replaceAll("[\\p{Cntrl}\\u00a7]", " ").strip();
        this.serverName = display.isEmpty() ? "Minecraft server" : display.substring(0, Math.min(display.length(), 80));
        store.state().grants().values().stream().filter(ConnectionStore.Grant::revoked).forEach(g -> blocked.add(g.connectionId()));
    }
    void start() { worker.scheduleWithFixedDelay(this::pollSafely, 0, 5, TimeUnit.SECONDS); }
    void enroll(Set<String> targets) {
        long epoch = enrollmentEpoch.incrementAndGet();
        store.state().pending().keySet().forEach(id -> enrollmentOutcomes.put(id, "enrollment_superseded"));
        worker.execute(() -> {
            try {
                store.state().pending().keySet().forEach(id -> enrollmentOutcomes.put(id, "enrollment_superseded"));
                store.clearPending();
                if (closed || epoch != enrollmentEpoch.get()) return;
                var identity = store.state();
                JsonNode result = transport.post("/v2/enrollments", null, Map.of("serverId", identity.serverId(), "serverToken", identity.serverToken(), "serverName", serverName, "allowedScriptIds", targets.stream().sorted().toList(), "allScripts", targets.isEmpty(), "scopes", SCOPES.stream().sorted().toList()));
                String id = result.path("enrollmentId").asText(), browserUrl = result.path("browserUrl").asText();
                URI url = URI.create(browserUrl);
                if (!ORIGIN.getScheme().equals(url.getScheme()) || !ORIGIN.getAuthority().equals(url.getAuthority()) || !"/connect".equals(url.getPath()) || url.getRawQuery() != null || url.getRawFragment() == null || !url.getRawFragment().startsWith("code=")) throw new IOException("Invalid browser enrollment URL");
                if (closed || epoch != enrollmentEpoch.get()) { enrollmentOutcomes.put(id, "enrollment_superseded"); return; }
                long expiresAt = result.path("expiresAt").asLong();
                if (expiresAt <= System.currentTimeMillis() || expiresAt > System.currentTimeMillis() + 900000) throw new IOException("Invalid enrollment expiry");
                store.pending(id, targets, expiresAt, targets.isEmpty());
                notice.accept("Open this private link to approve a named connection: " + browserUrl);
                notice.accept(targets.isEmpty() ? "Choose full native-script access or selected IDs in the browser. This private one-use link expires in 10 minutes." : "Browser approval is restricted to these exact feature IDs: " + targets.stream().sorted().toList() + ". The private link expires in 10 minutes.");
            } catch (Exception failure) { if (!closed) notice.accept("CONNECT_UNAVAILABLE: enrollment could not be completed. Your existing local grants were kept; retry /ais connect."); }
        });
    }
    boolean blocked(String id) { return closed || blocked.contains(id); }
    void disconnect(String id) {
        if (id == null) {
            enrollmentEpoch.incrementAndGet();
            blocked.addAll(store.state().grants().keySet()); store.state().pending().keySet().forEach(enrollment -> enrollmentOutcomes.put(enrollment, "grant_revoked"));
        } else blocked.add(id);
        worker.execute(() -> {
            try {
                if (id == null) store.revokeAll(); else store.revoke(id);
                notice.accept("Disconnected " + (id == null ? "all connections" : id) + "; revocation saved locally.");
                transport.post(path("revoke"), store.state().serverToken(), id == null ? Map.of("all", true) : Map.of("connectionId", id));
            } catch (Exception failure) { if (!closed) notice.accept("Connection revoked locally; relay notification or persistence needs attention. Check server storage and retry /ais disconnect before restarting."); }
        });
    }
    private String path(String action) { return "/v2/servers/" + store.state().serverId() + "/" + action; }
    void pollOnce() throws Exception {
        store.expirePending(System.currentTimeMillis()).keySet().forEach(id -> enrollmentOutcomes.put(id, "enrollment_expired"));
        if (closed || (store.state().grants().isEmpty() && store.state().pending().isEmpty())) return;
        JsonNode result = transport.post(path("poll"), store.state().serverToken(), Map.of());
        if (!result.path("grants").isArray() || result.path("grants").size() > 32 || !result.path("requests").isArray() || result.path("requests").size() > 1) throw new IOException("Invalid relay poll");
        for (JsonNode offered : result.get("grants")) {
            if (closed) return;
            String id = offered.path("connectionId").asText(), enrollmentId = offered.path("enrollmentId").asText();
            try {
                if (offered.path("revoked").isBoolean() && offered.path("revoked").asBoolean() && id.matches("[0-9a-f-]{36}")) {
                    var local = store.state().grants().get(id);
                    if (local != null) {
                        blocked.add(id);
                        if (!local.revoked()) { store.revoke(id); grantChanged.accept(store.state().grants().get(id)); }
                    } else store.removePending(enrollmentId);
                    grantStates.put(id, fingerprint(offered) + ":revoked"); continue;
                }
                if (blocked.contains(id)) { if (!offered.path("revoked").asBoolean()) rejectGrant(offered, "grant_revoked"); continue; }
                String outcome = enrollmentOutcomes.get(enrollmentId);
                if (outcome != null && !store.state().grants().containsKey(id)) { rejectGrant(offered, outcome); continue; }
                if (offered.has("allScripts") && !offered.get("allScripts").isBoolean()) throw new IOException("INVALID_GRANT");
                var grant = new ConnectionStore.Grant(id, offered.path("name").asText(), strings(offered.get("allowedScriptIds")), strings(offered.get("scopes")), offered.path("revoked").asBoolean(), offered.path("allScripts").asBoolean(false));
                boolean fresh = !store.state().grants().containsKey(id);
                ConnectionStore.Grant accepted = store.accept(grant, enrollmentId);
                if (blocked.contains(id) || (fresh && enrollmentOutcomes.containsKey(enrollmentId))) {
                    blocked.add(id); store.revoke(id); grantChanged.accept(store.state().grants().get(id)); rejectGrant(offered, "grant_revoked"); continue;
                }
                if (accepted.revoked()) blocked.add(id);
                grantChanged.accept(accepted);
                String stateHash = fingerprint(offered);
                if (!accepted.revoked() && !stateHash.equals(acknowledged.get(id))) { transport.post(path("ack"), store.state().serverToken(), Map.of("connectionId", id)); acknowledged.put(id, stateHash); }
                grantStates.put(id, stateHash + ":accepted");
                if (fresh && !accepted.revoked()) notice.accept("Approved connection " + accepted.name() + " (" + id + ") for " + (accepted.allScripts() ? "ALL NATIVE .aether SCRIPTS, including new feature IDs." : "exact feature IDs: " + accepted.allowedScriptIds().stream().sorted().toList()));
            } catch (IOException rejected) {
                String reason = switch (String.valueOf(rejected.getMessage())) {
                    case "IMMUTABLE_GRANT_CHANGED" -> "grant_mismatch";
                    case "NO_LOCAL_ENROLLMENT" -> "enrollment_unknown";
                    case "Connection limit reached" -> "capacity";
                    default -> "invalid_grant";
                };
                rejectGrant(offered, reason);
            }
        }
        for (JsonNode request : result.get("requests")) {
            if (closed) return;
            String connectionId = request.path("connectionId").asText(), id = request.path("id").asText();
            Map<String, Object> response;
            try {
                var grant = store.state().grants().get(connectionId); String operation = request.path("operation").asText();
                if (blocked(connectionId) || grant == null || grant.revoked() || !grant.scopes().contains(operation) || !request.path("arguments").isObject()) throw new SecurityException("CONNECTION_DENIED");
                Map<String, Object> arguments = FeatureParser.JSON.convertValue(request.get("arguments"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                if (!operation.equals("status") && !operation.equals("scripts.list")) {
                    if (!(arguments.get("id") instanceof String target)) throw new SecurityException("TARGET_DENIED");
                    FeatureParser.requireId(target);
                    if (!grant.allScripts() && !grant.allowedScriptIds().contains(target)) throw new SecurityException("TARGET_DENIED");
                }
                response = Map.of("connectionId", connectionId, "id", id, "result", handler.execute(grant, operation, arguments));
            } catch (Exception rejected) {
                boolean textRequired = rejected instanceof IOException && rejected.getMessage() != null && rejected.getMessage().startsWith("TEXT_SOURCE_REQUIRED");
                String code = textRequired ? "TEXT_SOURCE_REQUIRED" : rejected instanceof dev.aetherscript.core.ValidationException ? "VALIDATION_FAILED" : "REQUEST_REJECTED";
                String message = rejected instanceof dev.aetherscript.core.ValidationException
                    ? dev.aetherscript.core.FeatureManager.display(rejected.getMessage())
                    : textRequired ? "Owner must convert this ID to one safe .aether file first; existing YAML/JSON and duplicate source files are never overwritten." : "Request rejected by the local server grant or script engine.";
                response = Map.of("connectionId", connectionId, "id", id, "error", Map.of("code", code, "message", message));
            }
            if (!closed) transport.post(path("respond"), store.state().serverToken(), response);
        }
    }
    private void rejectGrant(JsonNode offered, String reason) throws IOException {
        String id = offered.path("connectionId").asText(), state = fingerprint(offered) + ":" + reason;
        if (!state.equals(grantStates.put(id, state))) notice.accept("Rejected connection offer " + dev.aetherscript.core.FeatureManager.display(id) + " (" + reason + "); local access was not expanded. Use /ais connect for a fresh approval link.");
        if (!id.matches("[0-9a-f-]{36}") || state.equals(terminalStates.get(id))) return;
        try {
            if (reason.equals("grant_revoked") && store.state().grants().containsKey(id)) transport.post(path("revoke"), store.state().serverToken(), Map.of("connectionId", id));
            else transport.post(path("reject"), store.state().serverToken(), Map.of("connectionId", id, "reason", reason));
            terminalStates.put(id, state);
        } catch (RelayRejected terminal) {
            if (terminal.status == 409 || terminal.status == 401) terminalStates.put(id, state);
        } catch (Exception unavailable) { /* Retry notification on the next poll without repeating the grant warning. */ }
    }
    private static String fingerprint(JsonNode value) throws IOException { return FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(value)); }
    private static Map<String, String> boundedMap() {
        return Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 64; }
        });
    }
    private static final class RelayRejected extends IOException {
        final int status;
        RelayRejected(int status) { super("Relay rejected request"); this.status = status; }
    }
    private void pollSafely() {
        try { pollOnce(); offlineNotice = false; }
        catch (Exception failure) { if (!closed && !offlineNotice) { notice.accept("Connection relay unavailable; local grants are preserved. Reconnecting automatically."); offlineNotice = true; } }
    }
    private static Set<String> strings(JsonNode array) throws IOException {
        if (array == null || !array.isArray() || array.size() > 128) throw new IOException("Invalid grant list");
        Set<String> result = new HashSet<>();
        for (JsonNode value : array) if (!value.isTextual() || !result.add(value.asText())) throw new IOException("Invalid grant list");
        return Set.copyOf(result);
    }
    private static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (path, token, body) -> {
            byte[] payload = FeatureParser.JSON.writeValueAsBytes(body);
            if (payload.length > 65536) throw new IOException("Relay payload too large");
            var builder = HttpRequest.newBuilder(ORIGIN.resolve(path)).timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json");
            if (token != null) builder.header("Authorization", "Bearer " + token);
            var future = client.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build(), responseInfo -> new BoundedBodySubscriber());
            try {
                var response = future.get(30, TimeUnit.SECONDS);
                if (response.statusCode() != 200 && response.statusCode() != 201) throw new RelayRejected(response.statusCode());
                return FeatureParser.JSON.readTree(response.body());
            } finally { future.cancel(true); }
        };
    }
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 65536) { subscription.cancel(); result.completeExceptionally(new IOException("Relay body too large")); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    @Override public void close() { closed = true; worker.shutdownNow(); }
}
