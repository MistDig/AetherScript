package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptValueTest {
    @Test void closedValuesRejectNativeObjectsAndMismatchedContents() {
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.INTEGER, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.LOCATION, new Object()));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.list(ScriptType.TEXT), List.of(new ScriptValue(ScriptType.INTEGER, 1L))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.optional(ScriptType.TEXT), Optional.of("raw")));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.TEXT, "x".repeat(2049)));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue.LocationSnapshot(UUID.randomUUID(), Double.NaN, 0, 0, 0, 0));
    }
    @Test void listSnapshotsAreImmutableAndBounded() {
        var input = new ArrayList<>(List.of(new ScriptValue(ScriptType.INTEGER, 1L)));
        var value = new ScriptValue(ScriptType.list(ScriptType.INTEGER), input);
        input.clear(); assertEquals(1, value.list().size());
        assertThrows(UnsupportedOperationException.class, () -> value.list().clear());
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.list(ScriptType.INTEGER), Collections.nCopies(257, new ScriptValue(ScriptType.INTEGER, 1L))));
        ScriptType type = ScriptType.INTEGER;
        for (int i = 0; i < 8; i++) type = ScriptType.list(type);
        ScriptType deep = type;
        assertThrows(IllegalArgumentException.class, () -> ScriptType.list(deep));
    }
    @Test void optionalAndListKindsAreDistinct() {
        var type = ScriptType.optional(ScriptType.LOCATION);
        assertFalse(type.isList()); assertEquals(ScriptType.Kind.OPTIONAL, type.kind());
        assertEquals(Optional.empty(), new ScriptValue(type, Optional.empty()).optional());
        assertEquals("LIST<INTEGER>", ScriptType.list(ScriptType.INTEGER).name());
        assertEquals(ScriptValue.Unit.INSTANCE, new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE).value());
    }
}
