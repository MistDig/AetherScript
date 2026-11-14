package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefaultsTest {
    private CompiledFeature compile(String functions, String body) {
        String source = "feature defaults version \"1.0.0\"\n" + functions + "\ncommand defaults permission \"aether.defaults\" sender any {\n" + body + "\n}\n";
        return new TextFrontend().parse(source.getBytes(StandardCharsets.UTF_8));
    }
    private List<String> run(CompiledFeature feature) { return feature.program().evaluate(List.of()).stream().map(Feature.MessageAction::text).toList(); }

    @Test void trailingDefaultsFillMissingArgumentsAndExplicitValuesWin() {
        var feature = compile("""
            fn sum(a: integer, b: integer = 2, c: integer = 3): integer { return a + b + c }
            """, "send text(sum(1))\nsend text(sum(1, 4))\nsend text(sum(1, 4, 5))");
        assertEquals(List.of("6", "8", "10"), run(feature));
        assertThrows(ValidationException.class, () -> compile("fn f(a: integer, b: integer = 2): integer { return a + b }", "send text(f())"));
        assertThrows(ValidationException.class, () -> compile("fn f(a: integer = 2): integer { return a }", "send text(f(1, 2))"));
    }
    @Test void scalarAndListConstantsRemainImmutableAcrossCallsAndInvocations() {
        var feature = compile("""
            fn grow(values: list<integer> = append([1], 2)): integer {
                var copy = values
                set copy = append(copy, 3)
                return length(copy)
            }
            fn scalar(textual: text = "hello", truth: boolean = !false): text {
                if truth { return textual }
                return "no"
            }
            fn nested(values: list<list<integer>> = [[1], [2]]): integer { return values[1][0] }
            fn empty(values: list<integer> = list<integer>()): integer { return length(values) }
            """, "send text(grow())\nsend text(grow())\nsend scalar()\nsend text(nested())\nsend text(empty())");
        assertEquals(List.of("3", "3", "hello", "2", "0"), run(feature));
        assertEquals(run(feature), run(feature));
    }
    @Test void defaultsRejectWrongTypesNamesCallsAndRequiredFollowingOptional() {
        for (String function : List.of(
                "fn f(a: integer = true): integer { return a }",
                "fn f(a: list<integer> = [true]): integer { return 1 }",
                "fn f(a: integer = 1, b: integer): integer { return a }",
                "fn f(a: integer, b: integer = a): integer { return b }",
                "fn g(): integer { return 1 }\nfn f(a: integer = g()): integer { return a }",
                "fn g(): integer { return 1 }\nfn f(a: boolean = true || g() == 1): boolean { return a }",
                "fn f(a: integer = 1 / 0): integer { return a }",
                "fn f(a: integer = 9223372036854775807 + 1): integer { return a }",
                "fn f(a: integer = 1): integer { if false { return true } return a }")) {
            var error = assertThrows(ValidationException.class, () -> compile(function, "send \"unused\""), function);
            assertTrue(error.line() > 0); assertTrue(error.column() > 0);
        }
    }
    @Test void unusedDefaultsAffectSemanticHashesAndCanonicalValuesIgnoreFormatting() {
        String body = "send \"unused\"";
        var first = compile("fn f(a: list<integer> = [1, 2]): integer { return length(a) }", body);
        var changed = compile("fn f(a: list<integer> = [1, 3]): integer { return length(a) }", body);
        var equivalent = compile("// comment\nfn f(a: list<integer> = [1, 1 + 1]): integer { return length(a) }", body);
        assertNotEquals(first.irHash(), changed.irHash());
        assertEquals(first.irHash(), equivalent.irHash());
        assertNotEquals(first.sourceHash(), equivalent.sourceHash());
        assertNotEquals(compile("fn f(a: list<integer> = list<integer>()): integer { return length(a) }", body).irHash(),
            compile("fn f(a: list<text> = list<text>()): integer { return length(a) }", body).irHash());
    }
    @Test void recursiveCallsUseIsolatedParameterFramesWithDefaults() {
        var feature = compile("""
            fn count(n: integer, extra: integer = 2): integer {
                if n == 0 { return extra }
                let result = count(n - 1)
                return result + extra
            }
            """, "send text(count(3, 10))");
        assertEquals(List.of("16"), run(feature));
    }
}
