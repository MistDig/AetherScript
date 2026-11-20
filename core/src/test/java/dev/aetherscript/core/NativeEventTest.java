package dev.aetherscript.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NativeEventTest {
    private CompiledFeature compile(String declarations) {
        return new FeatureParser().parse(("feature protect version \"1\"\n" + declarations).getBytes(StandardCharsets.UTF_8), "aether");
    }
    private CompiledFeature.EventData data(String kind, String world) {
        var fields = new LinkedHashMap<String, ScriptValue>();
        CompiledFeature.eventFields(kind).forEach((name, type) -> fields.put(name,
            new ScriptValue(type, type == ScriptType.INTEGER ? 7L : name.equals("world") ? world : name.equals("block_type") ? "STONE" : "Alex")));
        return new CompiledFeature.EventData(kind, fields);
    }
    @Test void typedPayloadConditionalCancellationIsFreshAndBuffered() {
        var feature = compile("""
            on block_break permission "*" bypass "protect.builder" {
                let target = world == "world" && block_type == "STONE" && x == 7
                if target {
                    send "Protected " + player_name + " at " + text(x)
                    cancel
                }
            }
            """);
        var handler = feature.program().eventHandlers().getFirst();
        assertTrue(feature.program().eventOnly());
        assertEquals("protect.builder", handler.bypassPermission());
        var result = handler.evaluate(data("block_break", "world"));
        assertTrue(result.cancelled()); assertEquals("Protected Alex at 7", result.messages().getFirst().text());
        assertFalse(handler.evaluate(data("block_break", "other")).cancelled());
        assertTrue(handler.evaluate(data("block_break", "other")).messages().isEmpty());
        assertTrue(feature.definition().requires().containsAll(Set.of("runtime.event.block_break", "runtime.event.cancel")));
        assertThrows(IllegalArgumentException.class, () -> handler.evaluate(data("block_place", "world")));
        assertThrows(IllegalArgumentException.class, () -> new CompiledFeature.EventData("join", Map.of()));
    }
    @ParameterizedTest @ValueSource(strings={"join", "quit", "block_break", "block_place"})
    void allKindsExposeTypedSnapshots(String kind) {
        var feature = compile("on " + kind + " permission \"events.use\" {\nsend player_name + \" in \" + world\n}");
        assertEquals("Alex in world", feature.program().eventHandlers().getFirst().evaluate(data(kind, "world")).messages().getFirst().text());
        assertTrue(feature.definition().requires().contains("runtime.event." + kind));
    }
    @ParameterizedTest @ValueSource(strings={
        "on damage permission \"*\" {\nsend \"x\"\n}",
        "on join permission \"*\" {\ncancel\n}",
        "on quit permission \"*\" {\nsend block_type\n}",
        "on block_break permission \"*\" {\nif x == \"7\" { cancel }\n}",
        "on join permission \"*\" {\narg x: text\n}",
        "on join permission \"*\" {\nrepeat 10 { send \"x\" }\n}",
        "on join permission \"*\" {\nlet a = [1,2]\n}",
        "on join permission \"*\" {\nlet a = map<text,text>()\n}",
        "on join permission \"*\" {\nsend player_name(1)\n}",
        "on join permission \"*\" bypass \"*\" {\nsend \"x\"\n}"
    })
    void rejectsUnsafeOrIllTypedProgramsAtSource(String declarations) {
        var error = assertThrows(ValidationException.class, () -> compile(declarations));
        assertTrue(error.line() > 0); assertTrue(error.column() > 0);
    }
    @Test void mixedCommandsAndEventsPreserveCommandAndSemanticIdentity() {
        String command = "command greet permission \"greet.use\" sender any {\nsend \"Hello\"\n}\n";
        var legacy = compile(command);
        var mixed = compile(command + "on join permission \"*\" {\nsend player_name\n}");
        assertFalse(mixed.program().eventOnly());
        assertEquals(legacy.definition().command().actions(), mixed.program().evaluate(List.of()));
        assertNotEquals(legacy.irHash(), mixed.irHash());
        assertEquals(mixed.irHash(), compile(command + "on join permission \"*\" {\nsend player_name\n}").irHash());
        assertNotEquals(mixed.irHash(), compile(command + "on quit permission \"*\" {\nsend player_name\n}").irHash());
        assertNotEquals(mixed.irHash(), compile(command + "on join permission \"*\" bypass \"build.use\" {\nsend player_name\n}").irHash());
    }
    @Test void eventOnlyFeaturesDoNotClaimCommands() {
        var eventOnly = compile("on join permission \"*\" {\nsend player_name\n}");
        var command = new FeatureParser().parse("feature other version \"1\"\ncommand protect permission \"other.use\" sender any {\nsend \"x\"\n}".getBytes(StandardCharsets.UTF_8), "aether");
        var registry = new Registry(); registry.replace(0, Map.of("protect", eventOnly, "other", command));
        assertFalse(registry.run("protect", true, permission -> true, message -> fail("Unexpected message")));
        assertTrue(registry.run("other", true, permission -> true, message -> {}));
    }
    @Test void restartRecoversEventOnlyAlongsideSameNamedCommand(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var scripts = temp.resolve("scripts"); java.nio.file.Files.createDirectories(scripts);
        java.nio.file.Files.writeString(scripts.resolve("protect.aether"), "feature protect version \"1\"\non block_place permission \"*\" {\ncancel\n}\n");
        java.nio.file.Files.writeString(scripts.resolve("other.aether"), "feature other version \"1\"\ncommand protect permission \"other.use\" sender any {\nsend \"x\"\n}\n");
        var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store);
        var initial = manager.startup(); assertEquals(2, initial.target().size()); store.commit(initial.durable());
        java.nio.file.Files.writeString(scripts.resolve("protect.aether"), "broken");
        var reopened = new FeatureManager(new ScriptStore(scripts), new RevisionStore(temp.resolve("storage"), 5)).startup();
        assertEquals(2, reopened.target().size());
        assertTrue(reopened.target().get("protect").program().eventOnly());
        assertTrue(reopened.target().get("protect").program().eventHandlers().getFirst().evaluate(data("block_place", "world")).cancelled());
    }

}
