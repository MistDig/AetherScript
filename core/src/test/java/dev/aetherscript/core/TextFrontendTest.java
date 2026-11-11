package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextFrontendTest {
    private static final String HEADER = "feature hello-ai version \"1.0.0\"\ncommand helloai permission \"aetherscript.example.hello\" sender any {\n";
    private CompiledFeature compile(String body) { return source(HEADER + body + "\n}\n"); }
    private CompiledFeature source(String source) { return new TextFrontend().parse(source.getBytes(StandardCharsets.UTF_8)); }
    private List<String> messages(String body) { return compile(body).definition().command().actions().stream().map(Feature.MessageAction::text).toList(); }

    @Test void variablesAndSelectedConditionsLowerToTheExistingIr() {
        String script = HEADER + "let greeting = \"Hello\"\nlet count = 2 + 3\nif count == 5 {\n send greeting + \" from AetherScript!\"\n} else {\n send \"Unexpected\"\n}\n}\n";
        var textual = source(script);
        var yaml = new FeatureParser().parse(EngineTest.YAML.getBytes(StandardCharsets.UTF_8), "yml");
        assertEquals(yaml.definition(), textual.definition()); assertEquals(yaml.irHash(), textual.irHash());
        assertEquals(FeatureParser.hash(script.getBytes(StandardCharsets.UTF_8)), textual.sourceHash());
        assertNotEquals(yaml.sourceHash(), textual.sourceHash());
    }
    @Test void arithmeticPrecedenceConversionAndBooleanExpressionsAreTyped() {
        assertEquals(List.of("true", "-3", "-1", "v7"), messages("""
            let value = 1 + 2 * 3
            send text(value == 7 && !(false || value < 7))
            send text(-7 / 2)
            send text(-7 % 2)
            send "v" + text(value)
            """));
    }
    @Test void lexicalScopesPermitShadowingAndResolveTheInitializerFromTheOuterScope() {
        assertEquals(List.of("inner 4", "3"), messages("""
            let value = 3
            if true {
                let value = value + 1
                send "inner " + text(value)
            }
            send text(value)
            """));
        var inaccessible = assertThrows(ValidationException.class, () -> compile("if true {\n let hidden = 3\n}\nsend text(hidden)"));
        assertTrue(inaccessible.reason().contains("Unknown variable 'hidden'"));
    }
    @Test void inactiveBranchesAreTypeCheckedWhileEvaluationShortCircuits() {
        assertEquals(List.of("yes"), messages("""
            if true || 1 / 0 == 0 {
                send "yes"
            } else {
                send text(1 / 0)
            }
            """));
        assertEquals(List.of("no"), messages("if false && 1 / 0 == 0 {\n send \"yes\"\n} else {\n send \"no\"\n}"));
        var error = assertThrows(ValidationException.class, () -> compile("if true {\n send \"yes\"\n} else {\n send unknown\n}"));
        assertEquals(6, error.line()); assertEquals(7, error.column()); assertTrue(error.reason().contains("Declare it with let"));
        assertThrows(ValidationException.class, () -> compile("if true {\n send \"yes\"\n} else {\n send 4\n}"));
    }
    @Test void integerExtremesAndExactArithmeticHaveDefinedBehavior() {
        assertEquals(List.of("-9223372036854775808", "9223372036854775807", "0"), messages("send text(-9223372036854775808)\nsend text(9223372036854775807)\nsend text(-9223372036854775808 % -1)"));
    }
    @ParameterizedTest @ValueSource(strings = {
        "9223372036854775807 + 1", "-9223372036854775808 - 1", "9223372036854775807 * 2",
        "-9223372036854775808 / -1", "-(-9223372036854775808)", "9223372036854775808", "-9223372036854775809",
        "1 / 0", "1 % 0"
    }) void invalidArithmeticFailsBeforeActivationAtItsSource(String expression) {
        var error = assertThrows(ValidationException.class, () -> compile("send text(" + expression + ")"));
        assertEquals(3, error.line()); assertTrue(error.column() > 1);
    }
    @ParameterizedTest @ValueSource(strings = {
        "let value = 1\nlet value = 2\nsend text(value)", "let value = later\nsend \"x\"", "let value = value\nsend \"x\"",
        "let value = 1\nvalue = 2\nsend text(value)", "send 4", "send true", "send \"x\" + 1", "send text(1 == true)",
        "send text(true + false)", "send text(\"x\" < \"y\")", "if 1 {\n send \"yes\"\n}",
        "send \"x\" send \"y\"", "send \"\"", "send \"unsafe\\nmessage\"", "send \"unsafe§message\"",
        "send \"bad\\u0041\"", "while true {\n send \"x\"\n}", "import java.lang.System"
    }) void unsupportedOrInvalidProgramsHaveLocations(String body) {
        var error = assertThrows(ValidationException.class, () -> compile(body));
        assertTrue(error.line() >= 3); assertTrue(error.column() >= 1); assertFalse(error.reason().isBlank());
    }
    @Test void literalEscapesCommentsCrLfAndUtf8HaveStableSemantics() {
        assertEquals(List.of("quote \" slash \\ café ☁"), messages("// ignored comment\nlet value = \"quote \\\" slash \\\\ café ☁\" // comment\nsend value"));
        String original = HEADER + "send \"hello\"\n}\n";
        assertEquals(source(original).definition(), source(original.replace("\n", "\r\n")).definition());
        assertEquals(source(original).definition(), source("\uFEFF" + original).definition());
        var malformed = assertThrows(ValidationException.class, () -> new TextFrontend().parse(new byte[]{(byte) 0xC3, 0x28}));
        assertEquals(1, malformed.line()); assertTrue(malformed.reason().contains("UTF-8"));
    }
    @Test void metadataUsesTheSamePermissionSenderAndIdConstraints() {
        assertThrows(ValidationException.class, () -> source(HEADER.replace("hello-ai version", "Hello-ai version") + "send \"x\"\n}"));
        assertThrows(ValidationException.class, () -> source(HEADER.replace("aetherscript.example.hello", "*") + "send \"x\"\n}"));
        assertThrows(ValidationException.class, () -> source(HEADER.replace("sender any", "sender all") + "send \"x\"\n}"));
        assertThrows(ValidationException.class, () -> source(HEADER.replace("\"1.0.0\"", "\"\"") + "send \"x\"\n}"));
        assertThrows(ValidationException.class, () -> source(HEADER + "send \"x\"\n}\ncommand other"));
        assertEquals("player", source(HEADER.replace("sender any", "sender player") + "send \"x\"\n}").definition().command().sender());
        var digits = source(HEADER.replace("feature hello-ai", "feature hello-123world").replace("command helloai", "command hello-123world") + "send \"x\"\n}");
        assertEquals("hello-123world", digits.definition().id());
        assertEquals("hello-123world", digits.definition().command().name());
    }
    @Test void sourceStructureAndPreparationWorkAreBounded() {
        assertThrows(ValidationException.class, () -> new TextFrontend().parse(new byte[65537]));
        assertThrows(ValidationException.class, () -> compile("send " + "(".repeat(33) + "\"x\"" + ")".repeat(33)));
        assertThrows(ValidationException.class, () -> compile("send text(" + "!".repeat(33) + "true)"));
        assertThrows(ValidationException.class, () -> compile("if true {\n".repeat(33) + "send \"x\"\n" + "}\n".repeat(33)));
        StringBuilder manyStatements = new StringBuilder();
        for (int i = 0; i < 257; i++) manyStatements.append("let value").append(i).append(" = 1\n");
        assertTrue(assertThrows(ValidationException.class, () -> compile(manyStatements + "send \"x\"")).reason().contains("Statement limit"));
        StringBuilder manyNodes = new StringBuilder();
        for (int i = 0; i < 33; i++) manyNodes.append("let value").append(i).append(" = ").append("1 + ".repeat(31)).append("1\n");
        assertTrue(assertThrows(ValidationException.class, () -> compile(manyNodes + "send \"x\"")).reason().contains("Expression node limit"));
        assertTrue(assertThrows(ValidationException.class, () -> source("\n".repeat(8193))).reason().contains("Token limit"));
        assertThrows(ValidationException.class, () -> compile("send \"x\"\n".repeat(33)));
        assertThrows(ValidationException.class, () -> compile("if false {\n send \"x\"\n}"));
        assertThrows(ValidationException.class, () -> compile("let chunk = \"" + "x".repeat(1024) + "\"\nsend chunk + chunk + \"x\""));
        String repeated = "let chunk = \"" + "x".repeat(2048) + "\"\n" + "send chunk\n".repeat(32);
        assertEquals(32, compile(repeated).definition().command().actions().size());
    }
    @Test void missingDelimitersAndUnclosedTextRemainActionable() {
        assertTrue(assertThrows(ValidationException.class, () -> source(HEADER + "send \"x\"\n")).reason().contains("Unclosed block"));
        assertTrue(assertThrows(ValidationException.class, () -> compile("send \"unclosed")).reason().contains("Text cannot cross"));
        assertThrows(ValidationException.class, () -> compile("send text((1 + 2)"));
    }
}
