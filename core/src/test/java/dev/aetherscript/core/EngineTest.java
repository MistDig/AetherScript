package dev.aetherscript.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    @TempDir Path temp;
    private final FeatureParser parser = new FeatureParser();
    static final String YAML = """
        schemaVersion: 1
        id: hello-ai
        version: 1.0.0
        requires: [runtime.message.send]
        commands:
          - name: helloai
            permission: aetherscript.example.hello
            sender: any
            actions:
              - type: message.send
                target: sender
                text: "Hello from AetherScript!"
        """;
    CompiledFeature parse(String text) { return parser.parse(text.getBytes(StandardCharsets.UTF_8), "yaml"); }
    @Test void equivalentFormatsHaveSameIrButDistinctSourceHashes() throws Exception {
        var yaml = parse(YAML);
        var json = parser.parse(Files.readAllBytes(Path.of("../examples/hello-ai.json")), "json");
        assertEquals(yaml.definition(), json.definition()); assertEquals(yaml.irHash(), json.irHash()); assertNotEquals(yaml.sourceHash(), json.sourceHash());
    }
    @ParameterizedTest @ValueSource(strings = {
        "schemaVersion: 1\nschemaVersion: 1", "!!java.net.URL [https://example.com]", "&a [*a]", "a: &x [1,2]\nb: *x",
        "---\na: b\n---\nc: d", "a: !!str tagged", "a: {<<: {b: c}}"
    }) void rejectsUnsafeYaml(String source) { assertThrows(ValidationException.class, () -> parse(source)); }
    @ParameterizedTest @CsvSource(delimiter='|', value={
        "schemaVersion: 1|schemaVersion: 2", "target: sender|target: everyone", "type: message.send|type: console.run",
        "sender: any|sender: all", "id: hello-ai|id: ../escape", "permission: aetherscript.example.hello|permission: '*'",
        "runtime.message.send|runtime.console.execute", "version: 1.0.0|version: 123", "name: helloai|name: hello/ai"
    }) void rejectsUnsupportedContract(String from, String to) { assertThrows(ValidationException.class, () -> parse(YAML.replace(from, to))); }
    @Test void rejectsUnknownAndMissingFields() {
        assertThrows(ValidationException.class, () -> parse(YAML + "unknown: true\n"));
        assertThrows(ValidationException.class, () -> parse(YAML.replace("    permission: aetherscript.example.hello\n", "")));
    }
    @Test void rejectsOversizeDepthAndInvalidUtf8() {
        assertThrows(ValidationException.class, () -> parser.parse(new byte[65537], "json"));
        assertThrows(ValidationException.class, () -> parse("[".repeat(33) + "0" + "]".repeat(33)));
        assertThrows(ValidationException.class, () -> parser.parse(("[".repeat(33) + "0" + "]".repeat(33)).getBytes(), "json"));
        assertThrows(ValidationException.class, () -> parser.parse(new byte[]{(byte)0xC3,(byte)0x28}, "yaml"));
    }
    @ParameterizedTest @ValueSource(strings={"{\"id\":1,\"id\":2}","{} {}","{unquoted:1}","{\"id\":NaN}","{\"id\":1,}","{\"id\":/*comment*/1}"})
    void rejectsNonStrictJson(String source) { assertThrows(ValidationException.class, () -> parser.parse(source.getBytes(), "json")); }
    @Test void boundedTextAndActionBudget() {
        assertThrows(ValidationException.class, () -> parse(YAML.replace("Hello from AetherScript!", "x".repeat(2049))));
        assertThrows(ValidationException.class, () -> parse(YAML.replace("Hello from AetherScript!", "§cRed")));
        String action = "      - type: message.send\n        target: sender\n        text: hello\n";
        String prefix = YAML.substring(0, YAML.indexOf("      - type"));
        assertEquals(32, parse(prefix + action.repeat(32)).definition().command().actions().size());
        assertThrows(ValidationException.class, () -> parse(prefix + action.repeat(33)));
    }
    @Test void rejectsDuplicateIdsAndMismatchedFilename() throws Exception {
        Files.writeString(temp.resolve("hello-ai.yml"), YAML); Files.writeString(temp.resolve("hello-ai.yaml"), YAML);
        assertThrows(ValidationException.class, () -> new ScriptStore(temp).read(null));
        Files.delete(temp.resolve("hello-ai.yaml")); Files.writeString(temp.resolve("other.yml"), YAML);
        assertThrows(ValidationException.class, () -> new ScriptStore(temp).read(null));
        assertThrows(ValidationException.class, () -> new ScriptStore(temp).read("../escape"));
    }
    @Test void rejectsOversizeOnDiskAndMissingFeature() throws Exception {
        Files.write(temp.resolve("hello-ai.json"), new byte[65537]);
        assertThrows(ValidationException.class, () -> new ScriptStore(temp).read(null));
        assertThrows(ValidationException.class, () -> new ScriptStore(temp).read("missing"));
    }
    @Test void invalidReloadPreservesGenerationAndBehavior() throws Exception {
        Registry registry = new Registry(); var store = new ScriptStore(temp);
        Files.writeString(temp.resolve("hello-ai.yml"), YAML);
        registry.replace(0, store.read(null)); var old = registry.snapshot();
        Files.writeString(temp.resolve("hello-ai.yml"), YAML.replace("message.send", "console.run"));
        assertThrows(ValidationException.class, () -> registry.replace(old.generation(), store.read(null)));
        assertSame(old, registry.snapshot()); List<String> messages = new ArrayList<>();
        assertTrue(registry.run("hello-ai", true, p -> true, messages::add)); assertEquals(List.of("Hello from AetherScript!"), messages);
    }
    @Test void hundredReloadsHaveOneEffectiveFeatureAndLatestBehavior() {
        Registry registry = new Registry();
        for (int i=0; i<100; i++) { var f=parse(YAML.replace("Hello from AetherScript!", "Message " + i)); registry.replace(i, Map.of("hello-ai",f)); }
        List<String> messages=new ArrayList<>(); registry.run("hello-ai",true,p->true,messages::add);
        assertEquals(List.of("Message 99"),messages); assertEquals(100,registry.snapshot().generation()); assertEquals(1,registry.snapshot().features().size());
    }
    @Test void permissionsAndSenderRestrictionsApplyAtInvocation() {
        Registry registry = new Registry(); registry.replace(0,Map.of("hello-ai",parse(YAML.replace("sender: any","sender: player"))));
        assertFalse(registry.run("hello-ai",true,p->false,s->fail("denied effect")));
        assertFalse(registry.run("hello-ai",false,p->true,s->fail("console effect")));
        assertFalse(registry.run("missing",true,p->true,s->fail("missing effect")));
        assertTrue(registry.run("hello-ai",true,p->p.equals("aetherscript.example.hello"),s->{}));
    }
    @Test void revocationAndRetirementBlockRemainingEffects() {
        String action="      - type: message.send\n        target: sender\n        text: hi\n";
        var f=parse(YAML.substring(0,YAML.indexOf("      - type"))+action.repeat(2));
        Registry registry=new Registry(); registry.replace(0,Map.of("hello-ai",f)); AtomicBoolean grant=new AtomicBoolean(true); AtomicInteger effects=new AtomicInteger();
        assertFalse(registry.run("hello-ai",true,p->grant.get(),s->{effects.incrementAndGet();grant.set(false);})); assertEquals(1,effects.get());
        effects.set(0);
        assertFalse(registry.run("hello-ai",true,p->true,s->{effects.incrementAndGet();registry.replace(1,Map.of());})); assertEquals(1,effects.get());
    }
    @Test void snapshotsImmutableAndStaleReplacementDenied() {
        Registry registry=new Registry(); Map<String,CompiledFeature> candidate=new HashMap<>(Map.of("hello-ai",parse(YAML)));
        registry.replace(0,candidate); candidate.clear(); assertEquals(1,registry.snapshot().features().size());
        assertThrows(UnsupportedOperationException.class,()->registry.snapshot().features().clear());
        assertThrows(IllegalStateException.class,()->registry.replace(0,Map.of())); assertEquals(1,registry.snapshot().generation());
    }
    @Test void collisionRejectsWholeReplacement() {
        Registry registry=new Registry(); var first=parse(YAML); registry.replace(0,Map.of("hello-ai",first));
        var second=parse(YAML.replace("id: hello-ai","id: other"));
        assertThrows(ValidationException.class,()->registry.replace(1,Map.of("hello-ai",first,"other",second))); assertEquals(1,registry.snapshot().generation());
    }
    @Test void auditStructuredAndFailsOnUnwritableTarget() throws Exception {
        Path file=temp.resolve("audit.jsonl"); new AuditLog(file).append("request", "player:123", "reload", "INTENT", 0, Map.of("hello-ai",parse(YAML)));
        var record=FeatureParser.JSON.readTree(Files.readString(file)); assertEquals("INTENT",record.path("outcome").asText()); assertFalse(Files.readString(file).contains("Hello from"));
        assertThrows(java.io.IOException.class,()->new AuditLog(temp).append("r","p","reload","INTENT",0,Map.of()));
    }
}
