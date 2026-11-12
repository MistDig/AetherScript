package dev.aetherscript.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.aetherscript.core.FeatureParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PairingTest {
    private static final String CODE = "A".repeat(43), TOKEN = "b".repeat(43);
    @TempDir Path temp;
    private Map<String, Object> serverStatus() {
        return Map.of("version", "test", "ready", true, "busy", false, "generation", 7, "features", 2,
            "aiDeployment", "ABSENT", "recovery", "ENABLED", "secret", "must-not-be-exported");
    }
    private void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = FeatureParser.JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private URI endpoint(HttpServer server) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
    private HttpServer server() throws IOException { return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0); }
    private PairingProfile profile(HttpServer server) { return new PairingProfile(endpoint(server), TOKEN, Instant.now().plusSeconds(3600)); }
    private void initialize(Main main) {
        main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params",
            Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "test", "version", "1")))));
        main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
    }
    private com.fasterxml.jackson.databind.JsonNode status(Main main) {
        return main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params",
            Map.of("name", "aether_server_status", "arguments", Map.of())))).path("result");
    }
    @Test void explicitPairingAuthenticatesStatusAndExportsOnlyTheAllowedFields() throws Exception {
        HttpServer server = server(); AtomicInteger pairs = new AtomicInteger(), statuses = new AtomicInteger();
        server.createContext("/pair", exchange -> {
            assertEquals("POST", exchange.getRequestMethod()); assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = FeatureParser.JSON.readTree(exchange.getRequestBody()); assertEquals(CODE, body.path("code").asText()); assertEquals(1, body.size());
            pairs.incrementAndGet(); json(exchange, 200, Map.of("token", TOKEN, "scope", "status", "expiresAt", Instant.now().plusSeconds(3600).toString()));
        });
        server.createContext("/status", exchange -> {
            assertEquals("GET", exchange.getRequestMethod()); assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            statuses.incrementAndGet(); json(exchange, 200, serverStatus());
        });
        server.start();
        try {
            PairingProfile profile = BridgeClient.pair(endpoint(server), CODE);
            Map<String, Object> status = new BridgeClient(profile).status();
            assertEquals(true, status.get("connected")); assertEquals(false, status.get("deploymentEnabled"));
            assertEquals("status", status.get("scope")); assertFalse(status.toString().contains(TOKEN)); assertFalse(status.toString().contains("must-not-be-exported"));
            assertEquals(1, pairs.get()); assertEquals(1, statuses.get()); assertFalse(profile.toString().contains(TOKEN));
        } finally { server.stop(0); }
    }
    @Test void statusReportsRevocationAsAnErrorWithoutExposingRemoteBodiesOrCredentials() throws Exception {
        HttpServer server = server(); AtomicBoolean revoked = new AtomicBoolean();
        server.createContext("/status", exchange -> json(exchange, revoked.get() ? 401 : 200,
            revoked.get() ? Map.of("error", TOKEN) : serverStatus())); server.start();
        try {
            Main main = new Main(new BridgeClient(profile(server))); initialize(main);
            assertTrue(status(main).path("structuredContent").path("connected").asBoolean());
            revoked.set(true); var rejected = status(main);
            assertTrue(rejected.path("isError").asBoolean()); assertEquals("BRIDGE_UNAVAILABLE", rejected.path("structuredContent").path("code").asText());
            assertFalse(rejected.toString().contains(TOKEN)); assertFalse(rejected.path("structuredContent").path("connected").asBoolean());
            var denied = main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/call", "params",
                Map.of("name", "aether_deploy", "arguments", Map.of()))));
            assertEquals(-32602, denied.path("error").path("code").asInt());
        } finally { server.stop(0); }
    }
    @Test void profileRoundTripUsesPrivatePermissionsAndAtomicReplacement() throws Exception {
        Path path = temp.resolve("private/new/profile.json");
        PairingProfile first = new PairingProfile(URI.create("http://127.0.0.1:12345"), TOKEN, Instant.now().plusSeconds(3600));
        ProfileStore.save(path, first); assertEquals(TOKEN, ProfileStore.load(path).token());
        PairingProfile second = new PairingProfile(first.endpoint(), "c".repeat(43), first.expiresAt());
        ProfileStore.save(path, second); assertEquals(second.token(), ProfileStore.load(path).token());
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) assertEquals(PosixFilePermissions.fromString("rw-------"), posix.readAttributes().permissions());
        else assertEquals(1, Files.getFileAttributeView(path, AclFileAttributeView.class).getAcl().size());
        try (var files = Files.list(path.getParent())) { assertEquals(List.of("profile.json"), files.map(p -> p.getFileName().toString()).toList()); }
        ProfileStore.forget(path); assertFalse(Files.exists(path)); ProfileStore.forget(path);
    }
    @Test void relayProfileRoundTripPreservesRoutingAndScopesPrivately() throws Exception {
        Path path = temp.resolve("relay/profile.json");
        PairingProfile profile = new PairingProfile(URI.create("https://relay.example"), "b".repeat(64), Instant.now().plusSeconds(3600), "session", "server", List.of("status", "scripts.apply"));
        ProfileStore.save(path, profile);
        PairingProfile loaded = ProfileStore.load(path);
        assertEquals(profile.endpoint(), loaded.endpoint()); assertEquals("session", loaded.sessionId);
        assertEquals("server", loaded.serverId); assertEquals(profile.scopes, loaded.scopes); assertEquals(profile.token(), loaded.token());
        assertFalse(loaded.toString().contains(TOKEN));
    }
    @Test void unsafeOrMalformedProfilesFailClosed() throws Exception {
        Path path = temp.resolve("profile.json");
        PairingProfile value = new PairingProfile(URI.create("http://127.0.0.1:12345"), TOKEN, Instant.now().plusSeconds(3600));
        ProfileStore.save(path, value);
        Files.writeString(path, "{\"token\":\"" + TOKEN + "\"}");
        IOException malformed = assertThrows(IOException.class, () -> ProfileStore.load(path)); assertFalse(malformed.getMessage().contains(TOKEN));
        ProfileStore.save(path, value);
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
        else {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            var everyone = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName("Everyone");
            var entries = new ArrayList<>(acl.getAcl()); entries.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build()); acl.setAcl(entries);
        }
        assertThrows(IOException.class, () -> ProfileStore.load(path));
        assertThrows(IOException.class, () -> ProfileStore.save(path, value));
        Path directory = temp.resolve("directory.json"); Files.createDirectory(directory); assertThrows(IOException.class, () -> ProfileStore.load(directory));
    }
    @ParameterizedTest @ValueSource(strings = {
        "http://localhost:12345", "https://127.0.0.1:12345", "http://127.0.0.2:12345", "http://[::1]:12345",
        "http://example.com:12345", "http://127.0.0.1", "http://127.0.0.1:0", "http://127.0.0.1:65536",
        "http://user:secret@127.0.0.1:12345", "http://127.0.0.1:12345/status", "http://127.0.0.1:12345?token=x", "http://127.0.0.1:12345#x"
    }) void onlyTheLiteralLoopbackOriginIsAccepted(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> BridgeClient.validateEndpoint(URI.create(endpoint)));
    }
    @Test void redirectsAreNeverFollowedForPairingOrAuthenticatedStatus() throws Exception {
        HttpServer server = server(); AtomicInteger forwarded = new AtomicInteger();
        server.createContext("/pair", exchange -> { exchange.getResponseHeaders().set("Location", endpoint(server) + "/sink"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/status", exchange -> { exchange.getResponseHeaders().set("Location", endpoint(server) + "/sink"); exchange.sendResponseHeaders(307, -1); exchange.close(); });
        server.createContext("/sink", exchange -> { forwarded.incrementAndGet(); json(exchange, 200, serverStatus()); }); server.start();
        try {
            assertThrows(IOException.class, () -> BridgeClient.pair(endpoint(server), CODE));
            assertThrows(IOException.class, () -> new BridgeClient(profile(server)).status()); assertEquals(0, forwarded.get());
        } finally { server.stop(0); }
    }
    @Test void expiredCredentialsDoNotMakeARequest() throws Exception {
        HttpServer server = server(); AtomicInteger requests = new AtomicInteger();
        server.createContext("/status", exchange -> { requests.incrementAndGet(); json(exchange, 200, serverStatus()); }); server.start();
        try {
            PairingProfile expired = new PairingProfile(endpoint(server), TOKEN, Instant.now().minusSeconds(1));
            Main main = new Main(new BridgeClient(expired)); initialize(main); var result = status(main);
            assertEquals("SESSION_EXPIRED", result.path("structuredContent").path("code").asText()); assertTrue(result.path("isError").asBoolean()); assertEquals(0, requests.get());
        } finally { server.stop(0); }
    }
    @Test void oversizedOrMalformedResponsesAreRejected() throws Exception {
        HttpServer server = server(); AtomicInteger response = new AtomicInteger();
        server.createContext("/status", exchange -> {
            int kind = response.getAndIncrement();
            if (kind == 0) json(exchange, 200, Map.of("oversized", "x".repeat(BridgeClient.MAX_RESPONSE)));
            else if (kind == 1) json(exchange, 200, Map.of("version", "x", "ready", "true"));
            else {
                byte[] bytes = "{\"ready\":true,\"ready\":false}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            }
        }); server.start();
        try { var client = new BridgeClient(profile(server)); for (int i = 0; i < 3; i++) assertThrows(IOException.class, client::status); }
        finally { server.stop(0); }
    }
    @Test void cliKeepsCredentialsOutOfArgumentsAndProtocolOutput() throws Exception {
        HttpServer server = server();
        server.createContext("/pair", exchange -> json(exchange, 200, Map.of("token", TOKEN, "scope", "status", "expiresAt", Instant.now().plusSeconds(3600).toString())));
        server.createContext("/status", exchange -> json(exchange, 200, serverStatus())); server.start();
        try {
            Path path = temp.resolve("profile.json"); ByteArrayOutputStream output = new ByteArrayOutputStream(), errors = new ByteArrayOutputStream();
            int paired = GatewayCli.run(new String[]{"pair", "--endpoint", endpoint(server).toString(), "--profile", path.toString()},
                new ByteArrayInputStream((CODE + "\n").getBytes(StandardCharsets.UTF_8)), output, new PrintStream(errors));
            assertEquals(0, paired); assertTrue(Files.isRegularFile(path)); assertFalse(output.toString().contains(TOKEN)); assertFalse(output.toString().contains(CODE)); assertFalse(errors.toString().contains(CODE));
            output.reset(); errors.reset();
            String requests = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{}}}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"aether_server_status\",\"arguments\":{}}}\n";
            assertEquals(0, GatewayCli.run(new String[]{"serve", "--profile", path.toString()}, new ByteArrayInputStream(requests.getBytes(StandardCharsets.UTF_8)), output, new PrintStream(errors)));
            var responses = output.toString(StandardCharsets.UTF_8).lines().map(line -> { try { return FeatureParser.JSON.readTree(line); } catch (IOException error) { throw new AssertionError(error); } }).toList();
            assertEquals(2, responses.size()); assertTrue(responses.getLast().path("result").path("structuredContent").path("connected").asBoolean());
            assertEquals(0, errors.size()); assertFalse(output.toString().contains(TOKEN));
            assertEquals(0, GatewayCli.run(new String[]{"forget", "--profile", path.toString()}, InputStreamHolder.EMPTY, output, new PrintStream(errors))); assertFalse(Files.exists(path));
        } finally { server.stop(0); }
    }
    @Test void cliOfflineDefaultAndInvalidSetupHaveNoCredentialSideEffects() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(), errors = new ByteArrayOutputStream();
        byte[] ping = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, GatewayCli.run(new String[0], new ByteArrayInputStream(ping), output, new PrintStream(errors)));
        assertEquals(7, FeatureParser.JSON.readTree(output.toByteArray()).path("id").asInt()); assertEquals(0, errors.size());
        output.reset();
        assertEquals(2, GatewayCli.run(new String[]{"pair", "--code", CODE}, InputStreamHolder.EMPTY, output, new PrintStream(errors)));
        assertFalse(errors.toString().contains(CODE)); assertEquals(0, output.size());
        assertEquals(2, GatewayCli.run(new String[]{"serve", "--profile", temp.resolve("missing").toString()}, InputStreamHolder.EMPTY, output, new PrintStream(errors)));
    }
    private static final class InputStreamHolder { static final ByteArrayInputStream EMPTY = new ByteArrayInputStream(new byte[0]); }
}
