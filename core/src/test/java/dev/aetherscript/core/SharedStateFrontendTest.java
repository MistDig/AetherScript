package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedStateFrontendTest {
    private CompiledFeature compile(String body) {
        return new TextFrontend().parse(("feature shared version \"1\"\ncommand shared permission \"test.shared\" sender player {\n" + body + "\n}\n").getBytes(StandardCharsets.UTF_8));
    }
    @Test void infersReadFallbackAndCommitMapValueWithDistinctCapabilities() {
        var compiled = compile("""
            let key = player_key()
            let old = shared_read("party", key, 0)
            let writes = {key: old + 1}
            let committed = shared_commit("party", writes, list<text>())
            send text(committed)
            """);
        assertTrue(compiled.definition().requires().containsAll(List.of("state.shared.read", "state.shared.write", "player.identity.self")));
        var registry = StandardCallables.create();
        var read = CallableRegistry.specialize(registry.descriptor("shared_read"), ScriptType.INTEGER);
        assertEquals(List.of(ScriptType.TEXT, ScriptType.TEXT, ScriptType.INTEGER), read.parameters());
        assertEquals(ScriptType.INTEGER, read.result());
        var writes = ScriptType.map(ScriptType.TEXT, ScriptType.list(ScriptType.INTEGER));
        var commit = CallableRegistry.specialize(registry.descriptor("shared_commit"), writes);
        assertEquals(List.of(ScriptType.TEXT, writes, ScriptType.list(ScriptType.TEXT)), commit.parameters());
        assertEquals(ScriptType.BOOLEAN, commit.result());
        assertEquals(Set.of("state.shared.write"), commit.requiredCapabilities());
        assertEquals(Set.of("state.shared.read"), read.requiredCapabilities());
        var identity = registry.descriptor("player_key");
        assertEquals(ScriptType.TEXT, identity.result()); assertTrue(identity.playerRequired());
        assertEquals(Set.of("player.identity.self"), identity.requiredCapabilities());
    }
    @Test void rejectsWrongKeysDeletesFallbackArityAndNestedUnit() {
        for (String body : List.of(
            "let x = shared_commit(\"scope\", {1: 2}, list<text>())",
            "let x = shared_commit(\"scope\", {\"key\": 2}, [1])",
            "let x = shared_read(1, \"key\", 0)",
            "let x = shared_read(\"scope\", 1, 0)",
            "let x = shared_read(\"scope\", \"key\")",
            "let x = shared_read(\"scope\", \"key\", list<unit>())",
            "let x = shared_commit(\"scope\", map<text,unit>(), list<text>())"))
            assertThrows(ValidationException.class, () -> compile(body));
        var unit = ScriptType.record("Invalid", Map.of("nested", ScriptType.list(ScriptType.UNIT)));
        assertThrows(IllegalArgumentException.class, () -> CallableRegistry.specialize(StandardCallables.create().descriptor("shared_read"), unit));
    }
    @Test void authoringMetadataReportsPolymorphicSignaturesAndPreservesOwnStateContract() {
        var guide = AuthoringGuide.metadata(); var calls = guide.get("hostCallables");
        var byName = new HashMap<String, com.fasterxml.jackson.databind.JsonNode>();
        calls.forEach(call -> byName.put(call.get("name").asText(), call));
        assertEquals("[\"text\",\"text\",\"T\"]", byName.get("shared_read").get("parameters").toString());
        assertEquals("T", byName.get("shared_read").get("returns").asText());
        assertEquals("[\"text\",\"map<text,T>\",\"list<text>\"]", byName.get("shared_commit").get("parameters").toString());
        assertEquals("boolean", byName.get("shared_commit").get("returns").asText());
        for (String own : List.of("state_save", "state_load")) {
            assertEquals("[\"text\",\"T\"]", byName.get(own).get("parameters").toString());
            assertEquals("T inferred from argument 2; unit and containers containing unit are unsupported", byName.get(own).get("typeParameter").asText());
        }
        assertEquals("unit", byName.get("state_save").get("returns").asText());
        assertEquals("T", byName.get("state_load").get("returns").asText());
        assertTrue(guide.has("hostAuthorization")); assertTrue(guide.has("hostEffects"));
    }
    @Test void runtimeUsesTheSameSpecializedDescriptorAndChecksCapabilities() throws Exception {
        var registry = StandardCallables.create(); var at = new SourceLocation("$", 1, 1);
        var fallback = new ScriptValue(ScriptType.INTEGER, 7L);
        var arguments = List.of(new ScriptValue(ScriptType.TEXT, "party"), new ScriptValue(ScriptType.TEXT, "key"), fallback);
        InvocationContext allowed = new InvocationContext("shared", "rev", UUID.randomUUID(), Set.of("state.shared.read"), new ExecutionBudget(), () -> true,
            (descriptor, values, context) -> { assertEquals(ScriptType.INTEGER, descriptor.result()); return values.get(2); }, ignored -> {});
        InvocationContext denied = new InvocationContext("shared", "rev", UUID.randomUUID(), Set.of("state.shared.write"), new ExecutionBudget(), () -> true,
            (descriptor, values, context) -> { fail("Read must be denied before dispatch"); return fallback; }, ignored -> {});
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals(fallback, executor.submit(() -> registry.invoke("shared_read", arguments, allowed, at)).get());
            executor.submit(() -> assertThrows(ValidationException.class, () -> registry.invoke("shared_read", arguments, denied, at))).get();
        }
    }
}
