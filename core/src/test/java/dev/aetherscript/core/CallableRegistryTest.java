package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CallableRegistryTest {
    private static final SourceLocation AT = new SourceLocation("$.source", 7, 12);
    private static ScriptValue text(String value) { return new ScriptValue(ScriptType.TEXT, value); }
    private static CallableRegistry registry() {
        CallableRegistry registry = new CallableRegistry();
        registry.register(new CallableRegistry.Descriptor("test.echo", "echo", List.of(ScriptType.TEXT), ScriptType.TEXT,
                CallableRegistry.ExecutionContext.PURE, Set.of("test.echo"), true, "test", "echo failure", "Echo typed text", "test"));
        return registry;
    }
    private static InvocationContext context(UUID actor, Set<String> caps, ExecutionBudget budget, java.util.function.BooleanSupplier current, InvocationContext.HostInvoker host) {
        return new InvocationContext("example", "revision", actor, caps, budget, current, host, value -> {});
    }
    @Test void typedDispatchRejectsDuplicatesArgumentsAndResults() {
        CallableRegistry registry = registry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(registry.descriptor("echo")));
        InvocationContext ctx = context(UUID.randomUUID(), Set.of("test.echo"), new ExecutionBudget(), () -> true, (d,a,c) -> a.getFirst());
        assertEquals(text("hello"), registry.invoke("echo", List.of(text("hello")), ctx, AT));
        ValidationException bad = assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(), ctx, AT));
        assertEquals(7, bad.line()); assertEquals(12, bad.column());
        assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(new ScriptValue(ScriptType.INTEGER, 1L)), ctx, AT));
        InvocationContext badHost = context(UUID.randomUUID(), Set.of("test.echo"), new ExecutionBudget(), () -> true, (d,a,c) -> new ScriptValue(ScriptType.INTEGER, 1L));
        assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(text("x")), badHost, AT));
    }
    @Test void authorityAndStalenessAreCheckedBeforeAndAfterHost() {
        CallableRegistry registry = registry(); AtomicInteger invoked = new AtomicInteger();
        InvocationContext.HostInvoker host = (d,a,c) -> { invoked.incrementAndGet(); return a.getFirst(); };
        for (InvocationContext ctx : List.of(context(null, Set.of("test.echo"), new ExecutionBudget(), () -> true, host),
                context(UUID.randomUUID(), Set.of(), new ExecutionBudget(), () -> true, host),
                context(UUID.randomUUID(), Set.of("test.echo"), new ExecutionBudget(), () -> false, host)))
            assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(text("x")), ctx, AT));
        assertEquals(0, invoked.get());
        AtomicBoolean current = new AtomicBoolean(true);
        InvocationContext stale = context(UUID.randomUUID(), Set.of("test.echo"), new ExecutionBudget(), current::get, (d,a,c) -> { current.set(false); return a.getFirst(); });
        assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(text("x")), stale, AT));
        InvocationContext cancelled = context(UUID.randomUUID(), Set.of("test.echo"), new ExecutionBudget(), () -> true, (d,a,c) -> { c.cancel(); return a.getFirst(); });
        assertThrows(ValidationException.class, () -> registry.invoke("echo", List.of(text("x")), cancelled, AT));
    }
    @Test void sharedBudgetBoundsNestedCallsAndMessages() throws Exception {
        ExecutionBudget budget = new ExecutionBudget(3, 1, 1);
        try (ExecutionBudget.Scope ignored = budget.enter(AT)) { assertThrows(ValidationException.class, () -> budget.enter(AT)); }
        InvocationContext ctx = context(null, Set.of("runtime.message.send"), new ExecutionBudget(), () -> true, (d,a,c) -> null);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                for (int i=0; i<32; i++) ctx.send("x", AT);
                assertThrows(ValidationException.class, () -> ctx.send("overflow", AT));
            }).get();
        }
        assertEquals(32, ctx.budget().messagesUsed());
    }
    @Test void hostOperationsRequireVirtualThreadAndMapCheckedErrors() throws Exception {
        CallableRegistry registry = StandardCallables.create();
        InvocationContext ctx = context(UUID.randomUUID(), Set.of("scheduler.delay"), new ExecutionBudget(), () -> true,
                (d,a,c) -> { throw new java.io.IOException("private host details"); });
        assertThrows(ValidationException.class, () -> registry.invoke("delay", List.of(new ScriptValue(ScriptType.INTEGER, 1L)), ctx, AT));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ValidationException failure = executor.submit(() -> assertThrows(ValidationException.class,
                    () -> registry.invoke("delay", List.of(new ScriptValue(ScriptType.INTEGER, 1L)), ctx, AT))).get();
            assertEquals(7, failure.line()); assertFalse(failure.getMessage().contains("private host details"));
        }
    }
}

