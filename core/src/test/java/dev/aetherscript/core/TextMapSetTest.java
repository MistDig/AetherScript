package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextMapSetTest {
    private CompiledFeature compile(String body) {
        return source("feature collections version \"1\"\ncommand collections permission \"aether.collections\" sender any {\n" + body + "\n}\n");
    }
    private CompiledFeature source(String text) { return new TextFrontend().parse(text.getBytes(StandardCharsets.UTF_8)); }
    private List<String> run(CompiledFeature feature) { return feature.program().evaluate(List.of()).stream().map(Feature.MessageAction::text).toList(); }
    private List<String> run(String body) { return run(compile(body)); }

    @Test void mapOperationsPreserveSnapshotsAndInsertionOrder() {
        assertEquals(List.of("1", "3", "false", "2", "b", "a", "3"), run("""
            let original = {"b": 1, "a": 2}
            let changed = put(original, "b", 3)
            send text(unwrap(get(original, "b")))
            send text(unwrap(get(changed, "b")))
            send text(is_some(get(original, "missing")))
            send text(length(remove(changed, "missing")))
            for key in keys(changed) { send key }
            send text(values(changed)[0])
            """));
    }
    @Test void setsDeduplicateAndIterateInInsertionOrder() {
        assertEquals(List.of("2", "1", "true", "2", "false"), run("""
            let original = set(2, 1, 2)
            for value in original { send text(value) }
            send text(contains(add(original, 3), 3))
            send text(length(original))
            send text(contains(remove(original, 2), 2))
            """));
    }
    @Test void equalityIsOrderIndependentAndRecursiveThroughOptionals() {
        assertEquals(List.of("true", "true", "true"), run("""
            send text({"a": [1], "b": [2]} == {"b": [2], "a": [1]})
            send text(set(1, 2) == set(2, 1))
            send text(get({"a": [1]}, "a") == get({"a": [1]}, "a"))
            """));
    }
    @Test void typedEmptyCollectionsAndPureDefaultsWork() {
        assertEquals(List.of("0", "0", "7"), run(source("""
            feature collections version "1"
            fn item(m: map<text,integer> = {"a": 7}, s: set<integer> = set<integer>()): integer {
                return unwrap(get(m, "a"))
            }
            command collections permission "aether.collections" sender any {
                send text(length(map<text,list<integer>>()))
                send text(length(set<integer>()))
                send text(item())
            }
            """)));
    }
    @Test void errorsRetainLocationsAndMapsRequireExplicitIteration() {
        for (String body : List.of("let x = {}", "let x = {1: true, 2: 3}", "let x = {[1]: 2}",
                "let x = set([1])", "let x = set()", "let x = put({1: 2}, true, 3)",
                "let x = get({1: 2}, true)", "for x in {1: 2} { send text(x) }")) {
            var error = assertThrows(ValidationException.class, () -> compile(body), body);
            assertTrue(error.line() > 0); assertTrue(error.column() > 0);
        }
        var duplicate = assertThrows(ValidationException.class, () -> run("let key = \"a\"\nlet m = {key: 1, key: 2}"));
        assertTrue(duplicate.reason().contains("Duplicate map key")); assertTrue(duplicate.line() > 0);
    }
    @Test void hashesRetainObservableOrderAndIgnoreComments() {
        assertEquals(compile("let x = {1: 2}").irHash(), compile("// comment\nlet x = {1: 2}").irHash());
        assertNotEquals(compile("let x = {1: 2, 3: 4}").irHash(), compile("let x = {3: 4, 1: 2}").irHash());
        assertNotEquals(compile("let x = set(1, 2)").irHash(), compile("let x = set(2, 1)").irHash());
    }
    @Test void boundsAndNestedEqualityUseSharedLimits() {
        String entries = java.util.stream.IntStream.range(0, 256).mapToObj(i -> i + ":" + i).collect(java.util.stream.Collectors.joining(","));
        assertEquals(List.of("256"), run("send text(length({" + entries + "}))"));
        var full = assertThrows(ValidationException.class, () -> run("let m = put({" + entries + "}, 256, 0)"));
        assertTrue(full.line() > 0); assertTrue(full.reason().contains("256"));
        assertEquals(List.of("256"), run("send text(length(put({" + entries + "}, 0, 7)))"));
        assertThrows(ValidationException.class, () -> compile("let x = " + "{1:".repeat(9) + "1" + "}".repeat(9)));
        StringBuilder body = new StringBuilder("let a0 = [1]\nlet b0 = [1]\n");
        for (int level = 1; level <= 4; level++) {
            for (String name : List.of("a", "b")) body.append("let ").append(name).append(level).append(" = [")
                .append(String.join(",", java.util.Collections.nCopies(16, name + (level - 1)))).append("]\n");
        }
        body.append("send text(get({1:a4},1) == get({1:b4},1))");
        var error = assertThrows(ValidationException.class, () -> run(body.toString()));
        assertTrue(error.reason().contains("budget"));
    }
    @Test void functionNamesTakePrecedenceOverCollectionBuiltins() {
        assertEquals(List.of("3", "4", "5"), run(source("""
            feature collections version "1"
            fn set(n: integer): integer { return n + 1 }
            fn get(n: integer): integer { return n + 2 }
            fn put(n: integer): integer { return n + 3 }
            command collections permission "aether.collections" sender any {
                send text(set(2))
                send text(get(2))
                send text(put(2))
            }
            """)));
    }
    @Test void copyingCollectionsUsesSharedBudget() {
        var error = assertThrows(ValidationException.class, () -> run("var s = set<integer>()\nrepeat 256 { set s = add(s, length(s)) }"));
        assertTrue(error.reason().contains("budget"));
    }
}
