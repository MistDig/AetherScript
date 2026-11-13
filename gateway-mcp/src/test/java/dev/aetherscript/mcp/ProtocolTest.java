package dev.aetherscript.mcp;
import dev.aetherscript.core.FeatureParser;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProtocolTest {
    Main server;
    JsonNode request(String method,Object params) { return server.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc","2.0","id",1,"method",method,"params",params))); }
    @BeforeEach void setup() {server=new Main();}
    void initialize() {
        assertEquals("2025-06-18",request("initialize",Map.of("protocolVersion","2025-06-18","capabilities",Map.of(),"clientInfo",Map.of("name","test","version","1"))).path("result").path("protocolVersion").asText());
        server.handle(FeatureParser.JSON.valueToTree(Map.of("jsonrpc","2.0","method","notifications/initialized")));
    }
    @Test void lifecycleAndDiscovery() {
        assertEquals(-32002,request("tools/list",Map.of()).path("error").path("code").asInt()); initialize();
        var tools=request("tools/list",Map.of()).path("result").path("tools"); assertEquals(4,tools.size());
        for(var tool:tools) assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean());
    }
    @Test void noMutationToolsOrFileTools() {
        initialize();
        for(String tool:List.of("aether_deploy","aether_stage","aether_rollback","write_file","execute_console","aether_feature_get"))
            assertEquals(-32602,request("tools/call",Map.of("name",tool,"arguments",Map.of())).path("error").path("code").asInt());
        assertEquals(-32601,request("aether_deploy",Map.of()).path("error").path("code").asInt());
    }
    @Test void validAndInvalidSourcesUseSharedParser() throws Exception {
        initialize(); String source=Files.readString(Path.of("../examples/hello-ai.json"));
        var valid=request("tools/call",Map.of("name","aether_validate","arguments",Map.of("source",source,"format","json"))).path("result");
        assertFalse(valid.path("isError").asBoolean()); assertTrue(valid.path("structuredContent").path("valid").asBoolean());
        assertFalse(valid.path("structuredContent").path("serverAuthorizationChecked").asBoolean());
        var bad=request("tools/call",Map.of("name","aether_validate","arguments",Map.of("source",source.replace("message.send","console.run"),"format","json"))).path("result");
        assertTrue(bad.path("isError").asBoolean());
        var target=request("tools/call",Map.of("name","aether_validate","arguments",Map.of("source",source.replace("\"target\": \"sender\"","\"target\": \"everyone\""),"format","json"))).path("result").path("structuredContent");
        assertTrue(target.path("line").asInt() > 0); assertTrue(target.path("column").asInt() > 0);
        assertEquals("$.commands[0].actions[0].target", target.path("path").asText());
        assertTrue(target.path("reason").asText().contains("Fix: set target to sender"));
    }
    @Test void statusNeverPretendsLiveConnection() {
        initialize(); var status=request("tools/call",Map.of("name","aether_server_status","arguments",Map.of())).path("result").path("structuredContent");
        assertFalse(status.path("connected").asBoolean());assertFalse(status.path("deploymentEnabled").asBoolean());
        assertEquals(-32602,request("tools/call",Map.of("name","aether_server_status","arguments",Map.of("path","secret"))).path("error").path("code").asInt());
    }
    @Test void textualAuthoringIsDiscoverableAndUsesTheSharedValidator() throws Exception {
        initialize();
        var capabilities = request("tools/call", Map.of("name", "aether_capabilities", "arguments", Map.of()))
            .path("result").path("structuredContent");
        assertTrue(capabilities.path("formats").toString().contains("aether"));
        assertTrue(capabilities.path("authoring").isObject());
        var tools = request("tools/list", Map.of()).path("result").path("tools");
        assertTrue(tools.get(2).path("inputSchema").path("properties").path("format").path("enum").toString().contains("aether"));
        String source = Files.readString(Path.of("../examples/hello-ai.aether"));
        var valid = request("tools/call", Map.of("name", "aether_validate", "arguments", Map.of("source", source, "format", "aether")))
            .path("result");
        assertFalse(valid.path("isError").asBoolean());
        assertEquals("hello-ai", valid.path("structuredContent").path("featureId").asText());
        assertEquals(FeatureParser.hash(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)), valid.path("structuredContent").path("sourceHash").asText());
        var invalid = request("tools/call", Map.of("name", "aether_validate", "arguments", Map.of(
            "source", source.replace("let count = 2 + 3", "let count = missing"), "format", "aether")))
            .path("result");
        assertTrue(invalid.path("isError").asBoolean());
        assertEquals(4, invalid.path("structuredContent").path("line").asInt());
        assertTrue(invalid.path("structuredContent").path("column").asInt() > 0);
    }
    @Test void syntheticTestComparesExactMessagesAndRejectsPlayerOnly() throws Exception {
        initialize(); String source = Files.readString(Path.of("../examples/hello-ai.json"));
        var feature = new dev.aetherscript.core.FeatureParser().parse(source.getBytes(java.nio.charset.StandardCharsets.UTF_8), "json");
        var expected = feature.definition().command().actions().stream().map(dev.aetherscript.core.Feature.MessageAction::text).toList();
        var result = request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source, "format", "json", "expectedMessages", expected))).path("result").path("structuredContent");
        assertTrue(result.path("matched").asBoolean()); assertEquals("synthetic-agent", result.path("sender").asText());
        var mismatch = request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source, "format", "json", "expectedMessages", List.of("wrong")))).path("result").path("structuredContent");
        assertFalse(mismatch.path("matched").asBoolean()); assertEquals(expected.getFirst(), mismatch.path("actualMessages").get(0).asText());
        var player = request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source.replace("\"sender\": \"any\"", "\"sender\": \"player\""), "format", "json", "expectedMessages", expected))).path("result").path("structuredContent");
        assertFalse(player.path("invoked").asBoolean()); assertFalse(player.path("matched").asBoolean());
    }
    @Test void runtimeArgumentsWorkThroughLocalTestAndAreExposedInItsSchema() throws Exception {
        initialize(); String source = Files.readString(Path.of("../examples/greet.aether"));
        var args = Map.of("source", source, "format", "aether", "arguments", List.of("Alex", "3", "false"), "expectedMessages", List.of("Hello Alex. Count 3"));
        var result = request("tools/call", Map.of("name", "aether_test", "arguments", args)).path("result");
        assertFalse(result.path("isError").asBoolean()); assertTrue(result.path("structuredContent").path("matched").asBoolean());
        var defaults = request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source, "format", "aether", "arguments", List.of("Sam"), "expectedMessages", List.of("Hello Sam! Count 1")))).path("result");
        assertTrue(defaults.path("structuredContent").path("matched").asBoolean());
        var missing = request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source, "format", "aether", "expectedMessages", List.of()))).path("result");
        assertTrue(missing.path("isError").asBoolean()); assertEquals("$.arguments[0]", missing.path("structuredContent").path("path").asText());
        assertEquals(3, missing.path("structuredContent").path("line").asInt());
        assertEquals(-32602, request("tools/call", Map.of("name", "aether_test", "arguments", Map.of("source", source, "format", "aether", "arguments", List.of(1), "expectedMessages", List.of()))).path("error").path("code").asInt());
        var tools = request("tools/list", Map.of()).path("result").path("tools");
        for (var tool : tools) if (tool.path("name").asText().equals("aether_test")) assertEquals(16, tool.path("inputSchema").path("properties").path("arguments").path("maxItems").asInt());
    }
    @Test void stdioIsJsonOnlyAndNotificationsHaveNoResponse() throws Exception {
        var input="{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}\n{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n";
        ByteArrayOutputStream output=new ByteArrayOutputStream(); server.serve(new ByteArrayInputStream(input.getBytes()),output);
        assertEquals(1,output.toString().lines().count()); assertEquals(7,FeatureParser.JSON.readTree(output.toByteArray()).path("id").asInt());
    }
    @Test void oversizedFramesTerminateWithoutResponse() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();server.serve(new ByteArrayInputStream(new byte[131073]),output); assertEquals(0,output.size());
    }
    @Test void malformedAndDuplicateJsonRejected() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();server.serve(new ByteArrayInputStream("{\"id\":1,\"id\":2}\n".getBytes()),output);
        assertEquals(-32700,FeatureParser.JSON.readTree(output.toByteArray()).path("error").path("code").asInt());
    }
}
