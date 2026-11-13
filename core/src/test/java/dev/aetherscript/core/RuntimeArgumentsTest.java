package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeArgumentsTest {
    private static final String HEADER = "feature hello-ai version \"1.0.0\"\ncommand helloai permission \"aetherscript.example.hello\" sender any {\n";
    @TempDir Path temp;
    private String source(String body) { return HEADER + body + "\n}\n"; }
    private CompiledFeature compile(String body) { return new FeatureParser().parse(source(body).getBytes(StandardCharsets.UTF_8), "aether"); }
    private Registry registry(String body) { var registry = new Registry(); registry.replace(0, Map.of("hello-ai", compile(body))); return registry; }
    private List<String> run(Registry registry, List<String> arguments) {
        List<String> actual = new ArrayList<>(); assertTrue(registry.run("hello-ai", arguments, false, permission -> true, actual::add)); return actual;
    }
    private String greeting() {
        return "arg name: text\narg count: integer = 1\narg excited: boolean = true\nif excited && count > 1 {\n send \"Hello \" + name + \" x\" + text(count) + \"!\"\n} else {\n send \"Hello \" + name + \".\"\n}";
    }
    @Test void argumentsAndBranchesAreEvaluatedFreshOnEachInvocation() {
        assertEquals(List.of("!", "-"), run(registry("send \"!\"\nsend \"-\""), List.of()));
        Registry registry = registry(greeting());
        assertEquals(List.of("Hello Alex x3!"), run(registry, List.of("Alex", "3", "true")));
        assertEquals(List.of("Hello Sam."), run(registry, List.of("Sam", "3", "false")));
        assertEquals(List.of("Hello Ada."), run(registry, List.of("Ada")));
        assertEquals(List.of("Hello Ada."), run(registry, List.of("Ada", "1")));
    }
    @Test void defaultsAreTypedConstantExpressionsAndMayProduceNoMessagesOnOneBranch() {
        Registry registry = registry("arg count: integer = 2 * 3\nif count > 0 {\n send text(count)\n}");
        assertEquals(List.of("6"), run(registry, List.of())); assertEquals(List.of(), run(registry, List.of("0")));
        assertEquals(List.of("-9223372036854775808"), run(registry("arg n: integer\nsend text(n)"), List.of("-9223372036854775808")));
        assertEquals(List.of("12"), run(registry("arg n: integer\nsend text(n)"), List.of("+0012")));
    }
    @ParameterizedTest @ValueSource(strings = {
        "arg name: text = 1\nsend name", "arg name text\nsend name", "arg value: number\nsend \"x\"",
        "arg x: text = \"x\"\narg y: text\nsend y", "arg x: text\narg x: text\nsend x",
        "arg x: text\narg y: text = x\nsend y", "let x = 1\narg y: integer\nsend text(y)",
        "if true {\n arg x: text\n send x\n}", "arg text: text\nsend text",
        "arg count: integer\nif count > 0 {\n send \"yes\"\n} else {\n send missing\n}",
        "arg flag: boolean\nif flag {\n send 1\n} else {\n send \"ok\"\n}"
    }) void invalidDeclarationsAndInactiveBranchTypeErrorsRejectBeforeActivation(String body) {
        var error = assertThrows(ValidationException.class, () -> compile(body)); assertTrue(error.line() >= 3); assertTrue(error.column() > 0);
    }
    @Test void argumentFailureAndRuntimeArithmeticEmitNoPartialMessages() {
        Registry registry = registry("arg count: integer\nsend \"before\"\nsend text(10 / count)");
        for (List<String> args : List.of(List.<String>of(), List.of("bad"), List.of("0"), List.of("1", "2"), List.of("9223372036854775808"))) {
            List<String> actual = new ArrayList<>(); var error = assertThrows(ValidationException.class, () -> registry.run("hello-ai", args, false, permission -> true, actual::add));
            assertTrue(actual.isEmpty()); assertFalse(error.reason().isBlank());
        }
        assertEquals(List.of("before", "5"), run(registry, List.of("2")));
        var missing = assertThrows(ValidationException.class, () -> run(registry, List.of()));
        assertEquals("$.arguments[0]", missing.path()); assertEquals(3, missing.line());
        assertThrows(ValidationException.class, () -> run(registry("arg flag: boolean\nsend text(flag)"), List.of("TRUE")));
    }
    @Test void runtimeOutputBoundsAndGlobalArgumentBoundsAreEnforced() {
        String tooManyDeclarations = "arg x: text\n" + "send \"x\"\n".repeat(33);
        assertThrows(ValidationException.class, () -> compile(tooManyDeclarations));
        StringBuilder declarations = new StringBuilder();
        for (int i = 0; i < 17; i++) declarations.append("arg a").append(i).append(": text\n");
        assertThrows(ValidationException.class, () -> compile(declarations + "send \"x\""));
        Registry registry = registry("arg name: text\nsend name + \"x\"");
        assertThrows(ValidationException.class, () -> run(registry, List.of("x".repeat(2048))));
        assertThrows(ValidationException.class, () -> run(registry, List.of("x".repeat(2049))));
        assertThrows(ValidationException.class, () -> run(registry, List.of("unsafe\nvalue")));
        assertThrows(ValidationException.class, () -> run(registry, Arrays.asList((String) null)));
        assertThrows(ValidationException.class, () -> run(registry, java.util.Collections.nCopies(17, "x")));
    }
    @Test void permissionRevocationAndRetirementStillGuardEveryEffect() {
        Registry registry = registry("arg name: text\nsend name\nsend name + \" again\"");
        List<String> actual = new ArrayList<>();
        assertFalse(registry.run("hello-ai", List.of("x"), false, permission -> false, actual::add)); assertTrue(actual.isEmpty());
        AtomicBoolean allowed = new AtomicBoolean(true);
        assertFalse(registry.run("hello-ai", List.of("x"), false, permission -> allowed.get(), message -> { actual.add(message); allowed.set(false); }));
        assertEquals(List.of("x"), actual); actual.clear();
        assertFalse(registry.run("hello-ai", List.of("x"), false, permission -> true, message -> { actual.add(message); registry.replace(1, Map.of()); }));
        assertEquals(List.of("x"), actual);
    }
    @Test void legacyHashesAndThreeArgumentCompiledConstructorRemainCompatible() throws Exception {
        CompiledFeature legacy = new FeatureParser().parse(EngineTest.YAML.getBytes(StandardCharsets.UTF_8), "yaml");
        assertEquals("d17e1187d57c74120a98827087200da6a3845b70b55b9e2ae578958656cf0cd2", legacy.irHash()); assertNull(legacy.program());
        String text = Files.readString(Path.of("../examples/hello-ai.aether"));
        CompiledFeature nativeLegacy = new FeatureParser().parse(text.getBytes(StandardCharsets.UTF_8), "aether");
        assertEquals(legacy.irHash(), nativeLegacy.irHash()); assertEquals(legacy.definition(), nativeLegacy.definition()); assertNull(nativeLegacy.program());
        assertEquals(legacy, new CompiledFeature(legacy.definition(), legacy.sourceHash(), legacy.irHash()));
        assertFalse(FeatureParser.JSON.writeValueAsString(legacy).contains("program"));
        Registry registry = new Registry(); registry.replace(0, Map.of("hello-ai", legacy));
        assertEquals(List.of("Hello from AetherScript!"), run(registry, List.of()));
        assertThrows(ValidationException.class, () -> run(registry, List.of("unexpected")));
    }
    @Test void runtimeIrIgnoresCommentsAndPositionsButIncludesBranchesAndDefaults() {
        CompiledFeature first = compile(greeting());
        CompiledFeature second = new FeatureParser().parse(source("// comment\n" + greeting()).getBytes(StandardCharsets.UTF_8), "aether");
        assertEquals(first.irHash(), second.irHash()); assertNotEquals(first.sourceHash(), second.sourceHash());
        assertEquals(first, compile(greeting()));
        assertNotEquals(first.irHash(), compile(greeting().replace("count > 1", "count > 2")).irHash());
        assertNotEquals(first.irHash(), compile(greeting().replace("integer = 1", "integer = 2")).irHash());
    }
    @Test void invalidReloadRecoveryAndRollbackRetainRuntimePrograms() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts")); Path file = scripts.resolve("hello-ai.aether");
        Files.writeString(file, source(greeting())); var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store); var registry = new Registry();
        var startup = manager.startup(); registry.replace(0, startup.target()); store.commit(startup.durable());
        assertEquals(List.of("Hello Alex x3!"), run(registry, List.of("Alex", "3")));
        var unchanged = manager.reload("hello-ai", registry.snapshot()); assertFalse(unchanged.durable().changed()); assertEquals(registry.snapshot().features(), unchanged.target());
        Files.writeString(file, source(greeting().replace("Hello ", "Welcome ")));
        var change = manager.reload("hello-ai", registry.snapshot()); registry.replace(1, change.target()); store.commit(change.durable());
        assertEquals(List.of("Welcome Alex x3!"), run(registry, List.of("Alex", "3")));
        var rollback = manager.rollback("hello-ai", registry.snapshot()); registry.replace(2, rollback.target()); store.commit(rollback.durable());
        assertEquals(List.of("Hello Alex x3!"), run(registry, List.of("Alex", "3")));
        Files.writeString(file, source(greeting().replace("integer = 1", "integer = \"bad\"")));
        assertThrows(FeatureManager.Rejected.class, () -> manager.reload("hello-ai", registry.snapshot()));
        var reopened = new RevisionStore(temp.resolve("storage"), 5);
        var restored = new FeatureManager(new ScriptStore(scripts), reopened).startup();
        var restarted = new Registry(); restarted.replace(0, restored.target());
        assertEquals(List.of("Hello Sam."), run(restarted, List.of("Sam", "3", "false")));
        assertEquals(registry.snapshot().features(), restored.target());
    }
}
