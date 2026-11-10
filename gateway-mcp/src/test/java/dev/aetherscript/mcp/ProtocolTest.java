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
        var tools=request("tools/list",Map.of()).path("result").path("tools"); assertEquals(3,tools.size());
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
