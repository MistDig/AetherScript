package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.FutureTask;
import static org.junit.jupiter.api.Assertions.*;

class CompilerSdkTest {
    private CallableRegistry.Descriptor descriptor(String name) {
        return new CallableRegistry.Descriptor("example." + name, name,
            List.of(ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER)), ScriptType.INTEGER,
            CallableRegistry.ExecutionContext.PURE, Set.of("example.inspect"), false,
            "test provider", "Provider unavailable", "Inspect typed map entries", "example provider");
    }
    private byte[] source(String name) {
        return ("feature sdk-demo version \"1\"\ncommand sdk-demo permission \"example.sdk\" sender any {\n"
            + "send text(" + name + "({\"wins\": 3}))\n}\n").getBytes(StandardCharsets.UTF_8);
    }
    @Test void compilerBindsAStableTypedProviderCatalogWithoutLeakingSetupMutation() throws Exception {
        var catalog = new CallableRegistry(); catalog.register(descriptor("entry_count"));
        var compiler = new FeatureParser(catalog);
        catalog.register(descriptor("late_count"));
        assertThrows(ValidationException.class, () -> compiler.parse(source("late_count"), "aether"));
        var feature = compiler.parse(source("entry_count"), "aether");
        assertEquals(Set.of("runtime.message.send", "example.inspect"), Set.copyOf(feature.definition().requires()));
        var actual = new ArrayList<String>();
        var context = new InvocationContext("sdk-demo", feature.sourceHash(), null,
            Set.copyOf(feature.definition().requires()), new ExecutionBudget(), () -> true,
            (operation, arguments, invocation) -> {
                assertEquals("entry_count", operation.dslName());
                assertEquals(1, arguments.getFirst().map().size());
                return new ScriptValue(ScriptType.INTEGER, 1L);
            }, actual::add);
        var call = new FutureTask<>(() -> feature.program().evaluate(List.of(), context));
        Thread.ofVirtual().start(call); call.get();
        assertEquals(List.of("1"), actual);
        assertThrows(ValidationException.class, () -> new FeatureParser().parse(source("entry_count"), "aether"));
    }
    @Test void snapshotsRejectRegistrationAndDirectFrontendAlsoBindsItsSnapshot() {
        var catalog = new CallableRegistry(); catalog.register(descriptor("entry_count"));
        var snapshot = catalog.snapshot();
        assertSame(snapshot, snapshot.snapshot());
        assertThrows(IllegalStateException.class, () -> snapshot.register(descriptor("injected_count")));
        var frontend = new TextFrontend(catalog); catalog.register(descriptor("late_count"));
        assertThrows(ValidationException.class, () -> frontend.parse(source("late_count")));
    }
}
