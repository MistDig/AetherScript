package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedRecordsTest {
    private CompiledFeature compile(String declarations, String body) {
        return new TextFrontend().parse(("feature records version \"1\"\n" + declarations + "\ncommand records permission \"test.records\" sender any {\n" + body + "\n}\n").getBytes(StandardCharsets.UTF_8));
    }
    @Test void constructorsFunctionsNestedAccessAndImmutableReplacement() {
        var compiled = compile("""
            record Progress(stage: integer, title: text)
            record Quest(progress: Progress)
            fn next(p: Progress): Progress {
                return Progress(p.stage + 1, p.title)
            }
            """, """
            var p = Progress(1, "Start")
            let old = p
            set p = next(p)
            let quests = [Quest(p)]
            send text(old.stage) + ":" + text(quests[0].progress.stage) + ":" + p.title
            """);
        assertEquals(List.of("1:2:Start"), compiled.program().evaluate(List.of()).stream().map(Feature.MessageAction::text).toList());
    }
    @Test void rejectsInvalidSchemasAndTypedConstruction() {
        for (String declaration : List.of("record Empty()", "record Dup(x: integer, x: text)", "record Recursive(x: Recursive)", "record A(x: Missing)"))
            assertThrows(ValidationException.class, () -> compile(declaration, "send \"x\""));
        for (String body : List.of("let p = P(\"x\")", "let p = P(1, 2)", "let p = P()", "let p = P(1)\nsend p.missing", "let p = P(1)\np.x = 2", "let p = P(1)\nlet q = Q(1)\nsend text(p == q)"))
            assertThrows(ValidationException.class, () -> compile("record P(x: integer)\nrecord Q(x: integer)", body));
    }
    @Test void valuesAreDefensiveTypedAndPersistWithSchema() {
        var fields = new LinkedHashMap<String, ScriptType>(); fields.put("stage", ScriptType.INTEGER); fields.put("title", ScriptType.TEXT);
        var type = ScriptType.record("Progress", fields); fields.clear();
        var values = new LinkedHashMap<String, ScriptValue>(); values.put("stage", new ScriptValue(ScriptType.INTEGER, 2L)); values.put("title", new ScriptValue(ScriptType.TEXT, "Start"));
        var original = new ScriptValue(type, values); values.clear();
        assertEquals(original, ScriptValueCodec.decode(ScriptValueCodec.encode(original)));
        assertThrows(UnsupportedOperationException.class, () -> original.record().clear());
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(type, Map.of("stage", new ScriptValue(ScriptType.TEXT, "bad"))));
        assertFalse(type.isHashable());
        assertEquals(type, CallableRegistry.specialize(StandardCallables.create().descriptor("state_save"), type).parameters().get(1));
        var unitRecord = ScriptType.record("Bad", Map.of("value", ScriptType.UNIT));
        assertThrows(IllegalArgumentException.class, () -> CallableRegistry.specialize(StandardCallables.create().descriptor("state_save"), unitRecord));
    }
}
