package dev.aetherscript.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Authenticated outbound relay transport. Credentials never appear in protocol output. */
final class RelayClient extends BridgeClient {
    private final PairingProfile profile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    RelayClient(PairingProfile profile) { super(profile); this.profile = profile; }
    static URI validateEndpoint(URI uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")))
            throw new IllegalArgumentException("Relay requires an HTTPS origin");
        return URI.create(uri.toString().replaceAll("/$", ""));
    }
    static PairingProfile pair(String pairing) throws IOException {
        try {
            URI uri = URI.create(pairing);
            if (!"aether".equals(uri.getScheme()) || !"pair".equals(uri.getHost()) || uri.getRawQuery() == null || uri.getRawFragment() != null) throw new IllegalArgumentException();
            Map<String,String> fields = new HashMap<>();
            for (String field : uri.getRawQuery().split("&")) {
                String[] parts = field.split("=", 2);
                if (parts.length != 2 || fields.putIfAbsent(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException();
            }
            if (!fields.keySet().equals(Set.of("relay", "code"))) throw new IllegalArgumentException();
            URI endpoint = validateEndpoint(URI.create(fields.get("relay")));
            JsonNode response = post(HttpClient.newHttpClient(), endpoint.resolve("/v1/pair/claim"), null, Map.of("code", fields.get("code")));
            List<String> scopes = new ArrayList<>();
            if (!response.path("scopes").isArray()) throw new IllegalArgumentException();
            for (JsonNode scope : response.path("scopes")) { if (!scope.isTextual()) throw new IllegalArgumentException(); scopes.add(scope.textValue()); }
            String session = response.path("sessionId").asText(), server = response.path("serverId").asText();
            if (!session.matches("[A-Za-z0-9_-]{1,128}") || server.isBlank()) throw new IllegalArgumentException();
            Instant expiry = Instant.ofEpochMilli(response.path("expiresAt").longValue());
            if (!expiry.isAfter(Instant.now()) || expiry.isAfter(Instant.now().plus(Duration.ofHours(25)))) throw new IllegalArgumentException();
            return new PairingProfile(endpoint, response.path("token").asText(), expiry, session, server, scopes);
        } catch (RuntimeException error) { throw new IOException("PAIRING_REJECTED"); }
    }
    @Override boolean live() { return true; }
    @Override List<String> grants() { return profile.scopes; }
    @Override Map<String,Object> status() throws IOException {
        JsonNode server = operation("status", FeatureParser.JSON.createObjectNode());
        if (server.has("error")) throw new IOException("RELAY_UNAVAILABLE");
        return Map.of("connected", true, "mode", "paired-relay", "liveStateAvailable", true, "deploymentEnabled", profile.scopes.contains("scripts.apply"), "server", server);
    }
    @Override JsonNode operation(String operation, JsonNode arguments) throws IOException {
        if (!profile.expiresAt().isAfter(Instant.now())) throw new SessionExpiredException();
        if (!profile.scopes.contains(operation)) throw new IOException("SCOPE_DENIED");
        URI base = profile.endpoint().resolve("/v1/sessions/" + profile.sessionId + "/");
        JsonNode accepted = post(http, base.resolve("requests"), profile.token(), Map.of("operation", operation, "arguments", arguments));
        String id = accepted.path("id").asText();
        if (id.isBlank() || id.length() > 128) throw new IOException("RELAY_UNAVAILABLE");
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode result = post(http, base.resolve("results"), profile.token(), Map.of("id", id));
            if (!result.path("pending").asBoolean()) {
                if (result.has("error")) return FeatureParser.JSON.createObjectNode().set("error", result.get("error"));
                if (!result.has("result")) throw new IOException("RELAY_UNAVAILABLE");
                return result.get("result");
            }
            try { Thread.sleep(Math.clamp(result.path("pollAfterMs").asInt(5000), 1000, 10000)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("RELAY_UNAVAILABLE"); }
        }
        throw new IOException("RELAY_TIMEOUT");
    }
    private static JsonNode post(HttpClient client, URI uri, String token, Object body) throws IOException {
        byte[] bytes = FeatureParser.JSON.writeValueAsBytes(body);
        if (bytes.length > 65536) throw new IOException("REQUEST_TOO_LARGE");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json").header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return exchange(client, request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
    }
}
