package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.FutureTask;
import static org.junit.jupiter.api.Assertions.*;

class TextHostCallTest {
    private CompiledFeature compile(String declarations, String body) {
        return new TextFrontend().parse(("feature homes version \"1\"\n" + declarations + "command homes permission \"aether.homes\" sender player {\n" + body + "\n}\n").getBytes(StandardCharsets.UTF_8));
    }
    @Test void homeCallsInferCapabilitiesAndPreserveOrdering() throws Exception {
        var compiled = compile("fn save(place: location): unit {\n state_put_location(\"home\", place)\n return\n}\n", "let place = player_location()\nsave(place)\nlet home = state_get_location(\"home\")\nif is_some(home) {\n delay(1)\n teleport_self(unwrap(home))\n send \"arrived\"\n}");
        assertEquals(Set.of("runtime.message.send", "player.location.read", "state.own", "scheduler.delay", "player.teleport.self"), Set.copyOf(compiled.definition().requires()));
        List<String> operations = new ArrayList<>();
        var location = new ScriptValue(ScriptType.LOCATION, new ScriptValue.LocationSnapshot(UUID.randomUUID(), 1, 2, 3, 0, 0));
        var context = new InvocationContext("homes", "rev", UUID.randomUUID(), Set.copyOf(compiled.definition().requires()), new ExecutionBudget(), () -> true,
            (descriptor, args, invocation) -> {
                operations.add(descriptor.dslName());
                return switch (descriptor.dslName()) {
                    case "player_location" -> location;
                    case "state_get_location" -> new ScriptValue(ScriptType.optional(ScriptType.LOCATION), Optional.of(location));
                    default -> new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE);
                };
            }, message -> operations.add(message));
        var task = new FutureTask<>(() -> compiled.program().evaluate(List.of(), context));
        Thread.ofVirtual().start(task); assertEquals(1, task.get().size());
        assertEquals(List.of("player_location", "state_put_location", "state_get_location", "delay", "teleport_self", "arrived"), operations);
        assertTrue(assertThrows(ValidationException.class, () -> compiled.program().evaluate(List.of())).reason().contains("HOST_CONTEXT_REQUIRED"));
    }
    @Test void nativeCallsAreTypeCheckedAndOptionalsAreNotCollections() {
        assertThrows(ValidationException.class, () -> compile("", "delay(\"bad\")"));
        assertThrows(ValidationException.class, () -> compile("", "let home = state_get_location(\"home\")\nlet bad = length(home)"));
        assertThrows(ValidationException.class, () -> compile("", "let home = state_get_location(\"home\")\nsend text(home)"));
        assertThrows(ValidationException.class, () -> compile("", "player_location()"));
    }
}
