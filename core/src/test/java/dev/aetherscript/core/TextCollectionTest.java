package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextCollectionTest {
    private CompiledFeature compile(String body) { return source("feature lists version \"1.0.0\"\ncommand lists permission \"aether.lists\" sender any {\n" + body + "\n}\n"); }
    private CompiledFeature source(String text) { return new TextFrontend().parse(text.getBytes(StandardCharsets.UTF_8)); }
    private List<String> run(CompiledFeature feature) { return feature.program().evaluate(List.of()).stream().map(Feature.MessageAction::text).toList(); }
    private List<String> run(String body) { return run(compile(body)); }

    @Test void listsAreImmutableAndInvocationsAreFresh() {
        var program = compile("""
            let original = [1, 2]
            var changed = original
            set changed = append(changed, 3)
            send text(length(original))
            send text(changed[2])
            send text(original == [1, 2])
            send text([[true], [false]][1][0])
            """);
        assertEquals(List.of("2", "3", "true", "false"), run(program));
        assertEquals(run(program), run(program));
    }
    @Test void typedEmptyListsAndFunctionParametersAndResultsWork() {
        var feature = source("""
            feature lists version "1.0.0"
            fn add(values: list<integer>): list<integer> {
                return append(values, 7)
            }
            command lists permission "aether.lists" sender any {
                send text(add(list<integer>())[0])
                send text(length(list<list<text>>()))
            }
            """);
        assertEquals(List.of("7", "0"), run(feature));
    }
    @Test void loopsSnapshotTheirCollectionAndJumpsTargetNearestLoop() {
        assertEquals(List.of("1", "3", "2", "1", "4", "9", "4"), run("""
            let item = 9
            var values = [1, 2]
            for item in values {
                set values = append(values, 3)
                repeat 2 {
                    if item == 1 { break }
                    send text(item)
                    break
                }
                for item in [1, 2, 3] {
                    if item == 2 { continue }
                    if item == 3 { break }
                    send text(item)
                }
                send text(length(values))
            }
            send text(item)
            send text(length(values))
            """));
    }
    @Test void allPathsAreTypedAndDiagnosticsHaveLocations() {
        for (String body : List.of("let x = []", "let x = [1, true]", "let x = append([1], false)",
                "let x = [1][true]", "for x in 1 { send text(x) }", "let x = [1] + [2]",
                "if false { let x = [1, false] }", "let x = [1] == [false]", "send text([1])")) {
            var error = assertThrows(ValidationException.class, () -> compile(body), body);
            assertTrue(error.line() > 0); assertTrue(error.column() > 0);
        }
    }
    @Test void indexesUseSigned64BitBoundsAndFailAtTheirSource() {
        for (String index : List.of("-1", "1", "9223372036854775807")) {
            var error = assertThrows(ValidationException.class, () -> run("send text([1][" + index + "] )"));
            assertTrue(error.reason().contains("out of bounds")); assertTrue(error.column() > 0);
        }
    }
    @Test void collectionBoundsAndSharedExecutionBudgetAreEnforced() {
        String items = String.join(",", java.util.Collections.nCopies(256, "1"));
        assertEquals(List.of("256"), run("send text(length([" + items + "]))"));
        assertThrows(ValidationException.class, () -> compile("let x = [" + items + ",1]"));
        assertThrows(ValidationException.class, () -> run("let x = append([" + items + "], 1)"));
        assertThrows(ValidationException.class, () -> compile("let x = " + "[".repeat(9) + "1" + "]".repeat(9)));
        assertThrows(ValidationException.class, () -> compile("let x = " + "list<".repeat(9) + "integer" + ">".repeat(9) + "()"));
        var error = assertThrows(ValidationException.class, () -> run("for x in [" + items + "] { repeat 256 { let y = x } }"));
        assertTrue(error.reason().contains("budget"));
    }
    @Test void nestedStructuralEqualityUsesTheSharedBudget() {
        StringBuilder body = new StringBuilder("let a0 = [1]\nlet b0 = [1]\n");
        for (int level = 1; level <= 4; level++) {
            for (String name : List.of("a", "b")) body.append("let ").append(name).append(level).append(" = [")
                .append(String.join(",", java.util.Collections.nCopies(16, name + (level - 1)))).append("]\n");
        }
        body.append("send text(a4 == b4)");
        var error = assertThrows(ValidationException.class, () -> run(body.toString()));
        assertTrue(error.reason().contains("budget"));
    }
    @Test void collectionNamesRemainValidScalarIdentifiersAndFunctionNames() {
        assertEquals(List.of("true"), compile("let list = 1\nlet integer = 2\nsend text(list < integer)").definition().command().actions().stream().map(Feature.MessageAction::text).toList());
        var feature = source("""
            feature lists version "1.0.0"
            fn length(n: integer): integer { return n + 1 }
            fn append(n: integer): integer { return n + 2 }
            fn list(n: integer): integer { return n + 3 }
            command lists permission "aether.lists" sender any {
                send text(length(1))
                send text(append(1))
                send text(list(1))
            }
            """);
        assertEquals(List.of("2", "3", "4"), run(feature));
    }
    @Test void semanticHashesDescribeCollectionsAndIgnoreComments() {
        var first = compile("send text([1, 2][0])");
        assertEquals(first.irHash(), compile("// comment\nsend text([1, 2][0])").irHash());
        assertNotEquals(first.irHash(), compile("send text([2, 1][0])").irHash());
        assertNotEquals(compile("let x = list<integer>()").irHash(), compile("let x = list<text>()").irHash());
    }
}
