package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TypedStateCallTest {
    private CompiledFeature compile(String body) {
        return new TextFrontend().parse(("feature typed version \"1\"\ncommand typed permission \"aether.typed\" sender player {\n" + body + "\n}\n").getBytes(StandardCharsets.UTF_8));
    }
    @Test void inferredCollectionCallsUseTypedHostDescriptorsAndCapabilities() throws Exception {
        var feature = compile("state_save(\"scores\", {\"a\": [1, 2]})\nlet scores = state_load(\"scores\", map<text,list<integer>>())\nsend text(unwrap(get(scores, \"a\"))[1])");
        assertEquals(Set.of("state.own", "runtime.message.send"), Set.copyOf(feature.definition().requires()));
        Map<String, String> rows = new HashMap<>();
        List<String> messages = new ArrayList<>();
        var context = new InvocationContext("typed", "revision", UUID.randomUUID(), Set.copyOf(feature.definition().requires()), new ExecutionBudget(), () -> true,
            (descriptor, values, invocation) -> {
                assertEquals(ScriptType.map(ScriptType.TEXT, ScriptType.list(ScriptType.INTEGER)), descriptor.parameters().get(1));
                String key = values.getFirst().text();
                if (descriptor.id().equals("state.own.save")) {
                    rows.put(key, ScriptValueCodec.encode(values.get(1)));
                    return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE);
                }
                assertEquals(values.get(1).type(), descriptor.result());
                return rows.containsKey(key) ? ScriptValueCodec.decode(rows.get(key)) : values.get(1);
            }, messages::add);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) { executor.submit(() -> feature.program().evaluate(List.of(), context)).get(); }
        assertEquals(List.of("2"), messages);
    }
    @Test void malformedCallsAndUnitValuesAreRejectedAtCompileTime() {
        for (String body : List.of("state_save(1, true)", "state_load(\"x\")", "state_save(\"x\", 1, 2)", "state_save(\"x\", delay(0))", "state_load(\"x\", [delay(0)])"))
            assertThrows(ValidationException.class, () -> compile(body), body);
        assertThrows(ValidationException.class, () -> compile("let x = state_load(\"x\", 0)\nsend x"));
    }
    @Test void typedCallsRequirePlayerAuthorityAndValidateHostReturnType() throws Exception {
        var registry = StandardCallables.create();
        var args = List.of(new ScriptValue(ScriptType.TEXT, "key"), new ScriptValue(ScriptType.INTEGER, 7L));
        var at = new SourceLocation("$.source", 3, 4);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var context : List.of(
                new InvocationContext("typed", "revision", null, Set.of("state.own"), new ExecutionBudget(), () -> true, (d,a,c) -> a.get(1), m -> {}),
                new InvocationContext("typed", "revision", UUID.randomUUID(), Set.of(), new ExecutionBudget(), () -> true, (d,a,c) -> a.get(1), m -> {}),
                new InvocationContext("typed", "revision", UUID.randomUUID(), Set.of("state.own"), new ExecutionBudget(), () -> true, (d,a,c) -> a.getFirst(), m -> {}))) {
                executor.submit(() -> assertThrows(ValidationException.class, () -> registry.invoke("state_load", args, context, at))).get();
            }
        }
    }
}
