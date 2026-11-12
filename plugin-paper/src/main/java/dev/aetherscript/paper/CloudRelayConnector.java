package dev.aetherscript.paper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Outbound HTTPS only. Relay credentials never confer permission beyond the local grant. */
final class CloudRelayConnector implements AutoCloseable {
    interface Handler { Map<String, Object> execute(String operation, Map<String, Object> arguments) throws Exception; }
    private final URI endpoint;
    private final String registrationToken, serverId;
    private final Handler handler;
    private final Consumer<String> notice;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "AetherScript-CloudRelay"); t.setDaemon(true); return t; });
    private volatile boolean closed;
    private volatile String sessionId, serverToken;
    CloudRelayConnector(Path config, Handler handler, Consumer<String> notice) throws IOException {
        if (!Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)) throw new IOException("relay.json must be a regular file");
        byte[] bytes;
        try (var input = Files.newInputStream(config, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(8193); }
        if (bytes.length > 8192) throw new IOException("relay.json too large");
        JsonNode json = FeatureParser.JSON.readTree(bytes);
        if (!json.isObject() || json.size() != 3 || !json.path("endpoint").isTextual() || !json.path("registrationToken").isTextual() || !json.path("serverId").isTextual()) throw new IOException("Invalid relay.json");
        endpoint = URI.create(json.get("endpoint").asText().replaceAll("/+$", ""));
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null || !endpoint.getPath().isEmpty()) throw new IOException("Relay endpoint must be an HTTPS origin");
        registrationToken = json.get("registrationToken").asText(); serverId = json.get("serverId").asText();
        if (registrationToken.length() < 32 || registrationToken.length() > 512 || !serverId.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("Invalid relay credentials");
        this.handler = handler; this.notice = notice;
    }
    void start() { worker.execute(this::run); }
    private void run() {
        try {
            JsonNode registered = post("/v1/sessions", registrationToken, Map.of("serverId", serverId, "scopes", List.of("status", "scripts.list", "scripts.read", "scripts.validate", "scripts.apply", "scripts.test"), "ttlSeconds", 86400));
            sessionId = registered.path("sessionId").asText(); serverToken = registered.path("serverToken").asText();
            if (!sessionId.matches("[A-Za-z0-9_-]{1,128}") || serverToken.isEmpty()) throw new IOException("Invalid relay session");
            if (closed) { revokeRemote(); return; }
            notice.accept("AetherScript remote manage pairing: aether://pair?relay=" + URLEncoder.encode(endpoint.toString(), java.nio.charset.StandardCharsets.UTF_8) + "&code=" + URLEncoder.encode(registered.path("pairingCode").asText(), java.nio.charset.StandardCharsets.UTF_8));
            while (!closed) {
                JsonNode polled = post(path("poll"), serverToken, Map.of());
                JsonNode requests = polled.path("requests");
                if (!requests.isArray() || requests.size() > 1) throw new IOException("Invalid relay queue");
                for (JsonNode request : requests) {
                    if (closed) break;
                    String id = request.path("id").asText(); Map<String, Object> response;
                    try {
                        if (!request.path("operation").isTextual() || !request.path("arguments").isObject()) throw new IllegalArgumentException("INVALID_REQUEST");
                        Map<String, Object> arguments = FeatureParser.JSON.convertValue(request.get("arguments"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        var result = handler.execute(request.get("operation").asText(), arguments);
                        response = Map.of("id", id, "result", result);
                    } catch (Exception failure) {
                        String message = failure.getMessage();
                        response = Map.of("id", id, "error", Map.of("code", "REQUEST_REJECTED", "message", message == null ? "Operation rejected" : dev.aetherscript.core.FeatureManager.display(message)));
                    }
                    if (!closed) post(path("respond"), serverToken, response);
                }
                Thread.sleep(Math.clamp(polled.path("pollAfterMs").asLong(5000), 1000, 15000));
            }
        } catch (Exception failure) {
            if (!closed) notice.accept("Remote relay disconnected; grant revoked locally. Run /ais remote again to reconnect.");
        } finally { closed = true; }
    }
    private String path(String action) { return "/v1/sessions/" + sessionId + "/" + action; }
    private JsonNode post(String path, String credential, Map<String, ?> body) throws Exception {
        byte[] payload = FeatureParser.JSON.writeValueAsBytes(body);
        if (payload.length > 65536) throw new IOException("RESPONSE_TOO_LARGE");
        var request = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + credential)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            byte[] bytes = input.readNBytes(65537);
            if ((response.statusCode() != 200 && response.statusCode() != 201) || bytes.length > 65536) throw new IOException("Relay request rejected");
            return FeatureParser.JSON.readTree(bytes);
        }
    }
    boolean live() { return !closed; }
    private void revokeRemote() { try { if (serverToken != null) post(path("revoke"), serverToken, Map.of()); } catch (Exception ignored) { } }
    @Override public void close() {
        closed = true; worker.shutdownNow();
        var revoke = new Thread(this::revokeRemote, "AetherScript-RelayRevoke"); revoke.setDaemon(true); revoke.start();
    }
}
