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
        assertEquals(9, tools.size());
        JsonNode apply = null;
        for (JsonNode tool : tools) if (tool.path("name").asText().equals("aether_scripts_apply")) apply = tool;
        assertNotNull(apply); assertFalse(apply.path("annotations").path("readOnlyHint").asBoolean());
        var args = Map.of("id", "hello-ai", "source", "source", "expectedHash", "ABSENT");
        assertTrue(request(main, "tools/call", Map.of("name", "aether_scripts_apply", "arguments", args)).path("result").path("isError").asBoolean());
        assertEquals(List.of("scripts.apply"), calls);
        assertEquals(-32602, request(main, "tools/call", Map.of("name", "aether_scripts_apply", "arguments", Map.of("id", "hello-ai", "source", "source"))).path("error").path("code").asInt());
        assertEquals(1, calls.size());
    }
    @Test void relayOriginsAndPersistedRoutingAreStrict() {
        for (String uri : List.of("http://relay.example", "https://user:password@relay.example", "https://relay.example/path", "https://relay.example?token=x", "https://relay.example#x"))
            assertThrows(IllegalArgumentException.class, () -> RelayClient.validateEndpoint(URI.create(uri)));
        assertThrows(IllegalArgumentException.class, () -> new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now(), "../escape", "server", List.of("status")));
        assertThrows(IllegalArgumentException.class, () -> new PairingProfile(URI.create("https://relay.example"), "a".repeat(64), Instant.now(), "session", "server", List.of("console.run")));
    }
}
