package dev.aetherscript.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeControlTest {
    @TempDir Path temp;
    private String source(String functions, String body) {
        return "feature counter version \"1\"\n" + functions + "\ncommand count permission \"example.count\" sender any {\n" + body + "\n}\n";
    }
    private CompiledFeature compile(String functions, String body) {
        return new TextFrontend().parse(source(functions, body).getBytes(StandardCharsets.UTF_8));
    }
    private List<String> run(CompiledFeature feature, String... args) {
        var registry = new Registry(); registry.replace(0, Map.of("counter", feature));
        List<String> messages = new ArrayList<>();
        assertTrue(registry.run("counter", List.of(args), false, permission -> true, messages::add));
        return messages;
    }
    @Test void mutationLoopsBreakAndContinueUseFreshInvocationLocals() {
        var feature = compile("", """
            arg limit: integer
            var index = 0
            var sum = 0
            while index < limit {
                set index = index + 1
                if index == 2 { continue }
                if index > 5 { break }
                set sum = sum + index
            }
            repeat 2 { set sum = sum + 10 }
            send text(sum)
            """);
        assertEquals(List.of("33"), run(feature, "10"));
        assertEquals(List.of("21"), run(feature, "1"));
        assertEquals(List.of("20"), run(feature, "0"));
    }
    @Test void countedLoopEvaluatesCountOnceAndNestedBreakIsLocal() {
        var feature = compile("", """
            var count = 3
            var sum = 0
            repeat count {
                set count = 0
                repeat 5 {
                    set sum = sum + 1
                    break
                }
            }
            repeat 0 { send "unreachable" }
            send text(sum)
            """);
        assertNotNull(feature.program()); assertEquals(List.of("3"), run(feature));
    }
    @Test void functionsAreTypedReusableAndRecursiveFramesDoNotOverwriteTheCaller() {
        String functions = """
            fn factorial(n: integer): integer {
                if n <= 1 { return 1 }
                return n * factorial(n - 1)
            }
            fn greeting(name: text, excited: boolean): text {
                if excited { return "Hello " + name + "!" } else { return "Hello " + name + "." }
            }
            """;
        var feature = compile(functions, "arg name: text\nsend greeting(name, true)\nsend text(factorial(6))");
        assertEquals(List.of("Hello MistDig!", "720"), run(feature, "MistDig"));
        assertEquals(List.of("Hello Alex!", "720"), run(feature, "Alex"));
    }
    @Test void functionLocalsAndBlockShadowingAreIsolated() {
        var feature = compile("""
            fn add(n: integer): integer {
                var sum = n
                repeat 3 { set sum = sum + n }
                if true { var sum = 100
                    set sum = 200
                }
                return sum
            }
            """, "var sum = 9\nsend text(add(2))\nsend text(sum)");
        assertEquals(List.of("8", "9"), run(feature));
    }
    @ParameterizedTest @ValueSource(strings = {
        "let n = 1\nset n = 2", "arg n: integer\nset n = 2", "var n = 1\nset n = true",
        "while 1 { break }", "repeat true { break }", "break", "continue", "return 1",
        "if false { set missing = 1 }", "var true = 2", "repeat 1 { let n = 1 }\nsend text(n)"
    }) void invalidControlAndAssignmentsAreRejectedEvenOnInactivePaths(String body) {
        var error = assertThrows(ValidationException.class, () -> compile("", body));
        assertTrue(error.line() > 0); assertTrue(error.column() > 0);
    }
    @ParameterizedTest @ValueSource(strings = {
        "fn f(n: integer): integer { return true }",
        "fn f(n: integer): integer { if n > 0 { return 1 } }",
        "fn f(n: integer, n: integer): integer { return n }",
        "fn f(): integer { break\nreturn 1 }",
        "fn f(): integer { arg n: integer\nreturn n }",
        "fn f(): integer { return missing }",
        "fn f(): number { return 1 }",
        "fn text(): text { return \"x\" }",
        "fn f(): integer { return later() }\nfn later(): integer { return 1 }"
    }) void invalidFunctionsRejectBeforeActivation(String functions) {
        var error = assertThrows(ValidationException.class, () -> compile(functions, "send \"x\""));
        assertTrue(error.line() > 0);
    }
    @Test void functionCallsValidateArityAndTypesAndCannotCaptureCommandArguments() {
        String functions = "fn f(n: integer): integer { return n }\n";
        for (String body : List.of("send text(f())", "send text(f(1, 2))", "send text(f(true))", "send text(missing(1))"))
            assertThrows(ValidationException.class, () -> compile(functions, body));
        assertThrows(ValidationException.class, () -> compile("fn f(): text { return name }", "arg name: text\nsend f()"));
        assertThrows(ValidationException.class, () -> compile(functions, "arg n: integer = f(1)\nsend text(n)"));
    }
    @Test void newStatementWordsRemainLegalLegacyIdentifiersInUnambiguousPositions() {
        var feature = compile("", "let integer = 2\nlet var = 3\nlet repeat = integer + var\nsend text(repeat)");
        assertNull(feature.program()); assertEquals(List.of("5"), run(feature));
    }
    @Test void infiniteLoopsAndRecursiveCallsAreBoundedAndEmitNoPartialEffects() {
        for (var feature : List.of(
            compile("", "send \"before\"\nwhile true {}"),
            compile("", "send \"before\"\nrepeat 9223372036854775807 {}"),
            compile("fn recurse(): integer { return recurse() }", "send \"before\"\nsend text(recurse())"),
            compile("fn many(n: integer): integer { if n == 0 { return 1 }\nreturn many(n - 1) + many(n - 1) }", "send \"before\"\nsend text(many(20))")
        )) {
            var registry = new Registry(); registry.replace(0, Map.of("counter", feature)); List<String> sent = new ArrayList<>();
            var error = assertThrows(ValidationException.class, () -> registry.run("counter", false, permission -> true, sent::add));
            assertTrue(sent.isEmpty()); assertTrue(error.reason().contains("budget") || error.reason().contains("depth"));
        }
    }
    @Test void runtimeFailuresHaveOriginalLocationAndCallTrace() {
        var feature = compile("fn divide(n: integer): integer { return 10 / n }\n", "arg n: integer\nsend text(divide(n))");
        var error = assertThrows(ValidationException.class, () -> run(feature, "0"));
        assertEquals(2, error.line()); assertTrue(error.reason().contains("called divide at line"));
        assertTrue(error.reason().contains("Division by zero"));
        assertThrows(ValidationException.class, () -> run(compile("", "repeat -1 { send \"x\" }")));
        assertThrows(ValidationException.class, () -> run(compile("", "repeat 33 { send \"x\" }")));
    }
    @Test void semanticHashIncludesFunctionBodiesAndControlButNotCommentsOrPositions() {
        String functions = "fn f(n: integer): integer { return n + 1 }\n";
        var first = compile(functions, "var n = f(2)\nsend text(n)");
        assertEquals(first.irHash(), compile("// comment\n" + functions, "var n = f(2)\nsend text(n)").irHash());
        assertNotEquals(first.irHash(), compile(functions.replace("+ 1", "+ 2"), "var n = f(2)\nsend text(n)").irHash());
    }
    @Test void runtimeControlSurvivesRestartAndInvalidEditPreservesWorkingRevision() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts")); Path file = scripts.resolve("counter.aether");
        Files.writeString(file, source("fn f(n: integer): integer { return n * 2 }", "arg n: integer\nsend text(f(n))"));
        var store = new RevisionStore(temp.resolve("storage"), 5); var manager = new FeatureManager(new ScriptStore(scripts), store);
        var startup = manager.startup(); store.commit(startup.durable());
        assertEquals(List.of("8"), run(startup.target().get("counter"), "4"));
        var registry = new Registry(); registry.replace(0, startup.target());
        var unchanged = manager.reload("counter", registry.snapshot());
        assertFalse(unchanged.durable().changed()); assertEquals(registry.snapshot().features(), unchanged.target());
        Files.writeString(file, source("", "var n = 1\nset n = true"));
        assertThrows(FeatureManager.Rejected.class, () -> manager.reload("counter", registry.snapshot()));
        var recovered = new FeatureManager(new ScriptStore(scripts), new RevisionStore(temp.resolve("storage"), 5)).startup();
        assertEquals(List.of("14"), run(recovered.target().get("counter"), "7"));
    }
}
