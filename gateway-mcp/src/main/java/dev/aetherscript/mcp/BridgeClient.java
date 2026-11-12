package dev.aetherscript.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Narrow owner-paired loopback client. Only pairing and read-only status endpoints exist here. */
class BridgeClient {
    static final int MAX_RESPONSE = 64 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private final PairingProfile profile;
    private final HttpClient http;

    BridgeClient(PairingProfile profile) {
        this.profile = profile;
        this.http = newHttpClient();
    }
    static PairingProfile pair(URI endpoint, String code) throws IOException {
        URI safe = validateEndpoint(endpoint);
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) throw new IOException("PAIRING_REJECTED: expected the 43-character code from the server console");
        byte[] body = FeatureParser.JSON.writeValueAsBytes(Map.of("code", code));
        HttpRequest request = HttpRequest.newBuilder(safe.resolve("/pair")).timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        JsonNode response = exchange(newHttpClient(), request);
        if (!response.isObject() || response.size() != 3 || !response.path("token").isTextual()
            || !response.path("scope").asText().equals("status") || !response.path("expiresAt").isTextual())
            throw new IOException("PAIRING_REJECTED: invalid bridge response");
        try {
            Instant expires = Instant.parse(response.path("expiresAt").textValue());
            Instant now = Instant.now();
            if (!expires.isAfter(now) || expires.isAfter(now.plus(Duration.ofHours(2)))) throw new IllegalArgumentException();
            return new PairingProfile(safe, response.path("token").textValue(), expires);
        } catch (RuntimeException error) { throw new IOException("PAIRING_REJECTED: invalid bridge credentials"); }
    }
    boolean live() { return false; }
    List<String> grants() { return List.of("status"); }
    JsonNode operation(String operation, JsonNode arguments) throws IOException { throw new IOException("LIVE_OPERATIONS_UNAVAILABLE"); }
    Map<String, Object> status() throws IOException {
        if (!profile.expiresAt().isAfter(Instant.now())) throw new SessionExpiredException();
        HttpRequest request = HttpRequest.newBuilder(profile.endpoint().resolve("/status")).timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json").header("Authorization", "Bearer " + profile.token()).GET().build();
        JsonNode response = exchange(http, request);
        if (!response.isObject() || !response.path("version").isTextual() || response.path("version").textValue().length() > 64
            || !response.path("ready").isBoolean() || !response.path("busy").isBoolean()
            || !response.path("generation").isIntegralNumber() || !response.path("generation").canConvertToLong() || response.path("generation").longValue() < 0
            || !response.path("features").isIntegralNumber() || !response.path("features").canConvertToInt() || response.path("features").intValue() < 0 || response.path("features").intValue() > 128
            || !response.path("aiDeployment").asText().equals("ABSENT") || !response.path("recovery").asText().equals("ENABLED"))
            throw new IOException("BRIDGE_UNAVAILABLE");
        Map<String, Object> server = Map.of("version", response.path("version").textValue(), "ready", response.path("ready").booleanValue(),
            "busy", response.path("busy").booleanValue(), "generation", response.path("generation").longValue(),
            "features", response.path("features").intValue(), "aiDeployment", "ABSENT", "recovery", "ENABLED");
        return Map.of("connected", true, "mode", "paired-readonly", "deploymentEnabled", false, "liveStateAvailable", true,
            "scope", "status", "sessionExpiresAt", profile.expiresAt().toString(), "server", server);
    }
    static URI validateEndpoint(URI endpoint) {
        if (endpoint == null || !"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
            || endpoint.getPort() < 1 || endpoint.getPort() > 65535 || endpoint.getRawUserInfo() != null
            || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
            || !(endpoint.getRawPath().isEmpty() || endpoint.getRawPath().equals("/")))
            throw new IllegalArgumentException("Endpoint must be http://127.0.0.1:PORT with no credentials, path, query or fragment");
        return URI.create("http://127.0.0.1:" + endpoint.getPort());
    }
    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector() {
                public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                public void connectFailed(URI uri, SocketAddress address, IOException error) { }
            }).build();
    }
    static JsonNode exchange(HttpClient http, HttpRequest request) throws IOException {
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if ((response.statusCode() != 200 && response.statusCode() != 202) || response.headers().firstValue("Content-Type")
                .filter(value -> value.split(";", 2)[0].trim().equalsIgnoreCase("application/json")).isEmpty())
                throw new IOException("BRIDGE_UNAVAILABLE");
            try (var parser = FeatureParser.JSON.createParser(response.body())) {
                JsonNode result = FeatureParser.JSON.readTree(parser);
                if (result == null || parser.nextToken() != null) throw new IOException("BRIDGE_UNAVAILABLE");
                return result;
            } catch (IOException error) { throw new IOException("BRIDGE_UNAVAILABLE"); }
        } catch (InterruptedException error) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw new IOException("BRIDGE_UNAVAILABLE");
        } catch (ExecutionException | TimeoutException error) {
            pending.cancel(true); throw new IOException("BRIDGE_UNAVAILABLE");
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new IOException("BRIDGE_UNAVAILABLE")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(new IOException("BRIDGE_UNAVAILABLE")); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
    static final class SessionExpiredException extends IOException {
        SessionExpiredException() { super("SESSION_EXPIRED"); }
    }
}

/** Credentials stay out of record-generated toString output and protocol responses. */
class PairingProfile {
    private final URI endpoint;
    final String sessionId, serverId;
    final List<String> scopes;
    private final String token;
    private final Instant expiresAt;
    PairingProfile(URI endpoint, String token, Instant expiresAt) {
        this(endpoint, token, expiresAt, null, null, List.of("status"));
    }
    PairingProfile(URI endpoint, String token, Instant expiresAt, String sessionId, String serverId, List<String> scopes) {
        this.endpoint = sessionId == null ? BridgeClient.validateEndpoint(endpoint) : RelayClient.validateEndpoint(endpoint);
        if (sessionId != null && (!sessionId.matches("[A-Za-z0-9_-]{1,128}") || serverId == null || serverId.isBlank() || serverId.length() > 128 || scopes.isEmpty() || !java.util.Set.of("status", "scripts.list", "scripts.read", "scripts.validate", "scripts.apply", "scripts.test").containsAll(scopes))) throw new IllegalArgumentException("Invalid relay credentials");
        this.sessionId = sessionId; this.serverId = serverId; this.scopes = List.copyOf(scopes);
        String tokenFormat = sessionId == null ? "[A-Za-z0-9_-]{43}" : "[0-9a-f]{64}";
        if (token == null || !token.matches(tokenFormat) || expiresAt == null) throw new IllegalArgumentException("Invalid pairing credentials");
        this.token = token; this.expiresAt = expiresAt;
    }
    URI endpoint() { return endpoint; }
    String token() { return token; }
    Instant expiresAt() { return expiresAt; }
    @Override public String toString() { return "PairingProfile[credentials redacted]"; }
}
