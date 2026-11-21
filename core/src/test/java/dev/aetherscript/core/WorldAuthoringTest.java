package dev.aetherscript.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class WorldAuthoringTest {
    private static CompiledFeature compile(String body) {
        return new TextFrontend().parse(("feature timer version \"1\"\n" + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test void scheduledLoopWaitsOncePerIterationAndBreakStopsIt() throws Exception {
        var feature = compile("""
            command timer permission "example.timer" sender player {
              every 20 repeat 3 {
                send "received"
                break
              }
            }
            """);
        var messages = new ArrayList<String>();
        var waits = new ArrayList<Long>();
        var caps = Set.copyOf(feature.definition().requires());
        assertEquals(Set.of("scheduler.repeat", "runtime.message.send"), caps);
        var context = new InvocationContext("timer", "rev", UUID.randomUUID(), caps, new ExecutionBudget(), () -> true,
            (descriptor, arguments, invocation) -> {
                assertEquals("scheduler.repeat", descriptor.id());
                waits.add(arguments.getFirst().integer());
                return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE);
            }, messages::add);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> feature.program().evaluate(List.of(), context)).get();
        }
        assertEquals(List.of(20L), waits);
        assertEquals(List.of("received"), messages);
    }

    @Test void eventEffectsAndTimerArgumentsHaveDistinctTypeChecks() {
        assertThrows(ValidationException.class, () -> compile("on join permission \"*\" {\n block_hearts(10)\n}\n"));
        assertThrows(ValidationException.class, () -> compile("on block_break permission \"*\" {\n block_hearts(true)\n}\n"));
        assertThrows(ValidationException.class, () -> compile("command timer permission \"example.timer\" sender player {\n every true repeat 3 {\n send \"x\"\n }\n}\n"));
    }
}
