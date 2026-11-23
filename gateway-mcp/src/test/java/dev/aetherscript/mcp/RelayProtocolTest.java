package dev.aetherscript.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RelayProtocolTest {
    private static JsonNode request(Main main, String method, Object params) {
        return main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)));
    }
    @Test void relayToolsForwardExactArgumentsAndReportRemoteFailure() {
        var profile = new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now().plusSeconds(60), "session", "server", List.of("status", "scripts.apply"));
        List<String> calls = new ArrayList<>();
        Main main = new Main(new BridgeClient(profile) {
            @Override boolean live() { return true; }
            @Override JsonNode operation(String operation, JsonNode arguments) {
                calls.add(operation);
                assertEquals("hello-ai", arguments.path("id").asText());
                assertEquals("ABSENT", arguments.path("expectedHash").asText());
                return FeatureParser.JSON.valueToTree(Map.of("error", Map.of("code", "SCOPE_DENIED")));
            }
        });
        request(main, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of()));
        main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
        JsonNode tools = request(main, "tools/list", Map.of()).path("result").path("tools");
        assertEquals(11, tools.size());
        JsonNode apply = null;
        for (JsonNode tool : tools) {
            if (tool.path("name").asText().equals("aether_scripts_apply")) apply = tool;
            if (tool.path("name").asText().equals("aether_scripts_test")) {
                assertFalse(tool.path("annotations").path("readOnlyHint").asBoolean());
                assertTrue(tool.path("annotations").path("destructiveHint").asBoolean());
            }
        }
        assertNotNull(apply); assertFalse(apply.path("annotations").path("readOnlyHint").asBoolean());
        var args = Map.of("id", "hello-ai", "source", "source", "expectedHash", "ABSENT");
        assertTrue(request(main, "tools/call", Map.of("name", "aether_scripts_apply", "arguments", args)).path("result").path("isError").asBoolean());
        assertEquals(List.of("scripts.apply"), calls);
        assertEquals(-32602, request(main, "tools/call", Map.of("name", "aether_scripts_apply", "arguments", Map.of("id", "hello-ai", "source", "source"))).path("error").path("code").asInt());
        assertEquals(1, calls.size());
    }
    @Test void liveTestForwardsOptionalTypedArgumentStringsAndRejectsMalformedArrays() {
        var profile = new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now().plusSeconds(60), "session", "server", List.of("scripts.test"));
        List<JsonNode> forwarded = new ArrayList<>();
        Main main = new Main(new BridgeClient(profile) {
            @Override boolean live() { return true; }
            @Override JsonNode operation(String operation, JsonNode arguments) {
                assertEquals("scripts.test", operation); forwarded.add(arguments.deepCopy());
                return FeatureParser.JSON.valueToTree(Map.of("matched", true));
            }
        });
        request(main, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of()));
        main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
        var args = Map.of("id", "greet", "arguments", List.of("Alex", "2"), "expectedMessages", List.of("Hi Alex"));
        assertTrue(request(main, "tools/call", Map.of("name", "aether_scripts_test", "arguments", args)).path("result").path("structuredContent").path("matched").asBoolean());
        assertEquals(List.of("Alex", "2"), FeatureParser.JSON.convertValue(forwarded.getFirst().path("arguments"), List.class));
        assertEquals(-32602, request(main, "tools/call", Map.of("name", "aether_scripts_test", "arguments", Map.of("id", "greet", "arguments", List.of(true), "expectedMessages", List.of()))).path("error").path("code").asInt());
        assertEquals(1, forwarded.size());
    }
    @Test void capabilitiesPreserveLocalGuideAndFetchOnlyExplicitlyAuthorizedServerMetadata() {
        for (boolean authorized : List.of(false, true)) {
            List<String> calls = new ArrayList<>();
            var profile = new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now().plusSeconds(60), "session", "server",
                authorized ? List.of("capabilities") : List.of("scripts.test"));
            Main main = new Main(new BridgeClient(profile) {
                @Override boolean live() { return true; }
                @Override List<String> grants() { return profile.scopes; }
                @Override JsonNode operation(String operation, JsonNode arguments) {
                    calls.add(operation); assertTrue(arguments.isEmpty());
                    return FeatureParser.JSON.valueToTree(Map.of("executionCapabilities", List.of("runtime.message.send"), "testPlayerId", "owner-selected"));
                }
            });
            request(main, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of()));
            main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
            JsonNode result = request(main, "tools/call", Map.of("name", "aether_capabilities", "arguments", Map.of())).path("result").path("structuredContent");
            assertEquals(dev.aetherscript.core.AuthoringGuide.metadata(), result.path("authoring"));
            assertEquals(authorized, result.path("serverAuthorizationChecked").asBoolean());
            assertEquals(authorized ? List.of("capabilities") : List.of(), calls);
            if (!authorized) assertTrue(result.path("serverCapabilities").path("freshApprovalRequired").asBoolean());
        }
    }
    @Test void testStatusForwardsPendingAndCompletionWithoutAcceptingActorOverrides() {
        var profile = new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now().plusSeconds(60), "session", "server", List.of("scripts.test.status"));
        List<JsonNode> calls = new ArrayList<>();
        String testId = UUID.randomUUID().toString();
        Main main = new Main(new BridgeClient(profile) {
            @Override boolean live() { return true; }
            @Override JsonNode operation(String operation, JsonNode arguments) {
                assertEquals("scripts.test.status", operation); calls.add(arguments.deepCopy());
                return FeatureParser.JSON.valueToTree(Map.of("pending", false, "testId", testId, "actualMessages", List.of("done"), "effects", List.of("teleport_self")));
            }
        });
        request(main, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of()));
        main.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
        JsonNode result = request(main, "tools/call", Map.of("name", "aether_scripts_test_status", "arguments", Map.of("id", "homes", "testId", testId))).path("result").path("structuredContent");
        assertEquals("done", result.path("actualMessages").get(0).asText());
        assertEquals("teleport_self", result.path("effects").get(0).asText());
        for (Object bad : List.of(Map.of("id", "homes", "testId", "bad"), Map.of("id", "homes", "testId", true), Map.of("id", "homes", "testId", testId, "testPlayerId", testId)))
            assertEquals(-32602, request(main, "tools/call", Map.of("name", "aether_scripts_test_status", "arguments", bad)).path("error").path("code").asInt());
        assertEquals(1, calls.size());
    }
    @Test void relayOriginsAndPersistedRoutingAreStrict() {
        for (String uri : List.of("http://relay.example", "https://user:password@relay.example", "https://relay.example/path", "https://relay.example?token=x", "https://relay.example#x"))
            assertThrows(IllegalArgumentException.class, () -> RelayClient.validateEndpoint(URI.create(uri)));
        assertThrows(IllegalArgumentException.class, () -> new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now(), "../escape", "server", List.of("status")));
        assertThrows(IllegalArgumentException.class, () -> new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now(), "session", "server", List.of("console.run")));
    }
}
