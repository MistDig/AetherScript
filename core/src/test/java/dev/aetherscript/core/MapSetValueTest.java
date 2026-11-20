package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapSetValueTest {
    private static ScriptValue integer(long n) { return new ScriptValue(ScriptType.INTEGER, n); }
    private static ScriptValue text(String s) { return new ScriptValue(ScriptType.TEXT, s); }

    @Test void snapshotsKeepOrderAndRejectMutation() {
        var input = new LinkedHashMap<ScriptValue, ScriptValue>();
        input.put(text("b"), integer(2)); input.put(text("a"), integer(1));
        var value = new ScriptValue(ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER), input);
        input.clear();
        assertEquals(List.of(text("b"), text("a")), new ArrayList<>(value.map().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> value.map().clear());
        assertThrows(UnsupportedOperationException.class, () -> value.map().entrySet().iterator().next().setValue(integer(3)));
        var elements = new LinkedHashSet<>(List.of(integer(2), integer(1)));
        var set = new ScriptValue(ScriptType.set(ScriptType.INTEGER), elements);
        elements.clear();
        assertEquals(List.of(integer(2), integer(1)), new ArrayList<>(set.set()));
        assertThrows(UnsupportedOperationException.class, () -> set.set().add(integer(3)));
    }
    @Test void exactTypesAndClosedContentsAreRequired() {
        var map = ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER);
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(map, Map.of("raw", 1L)));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(map, Map.of(integer(1), integer(2))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(map, Map.of(text("a"), text("wrong"))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.set(ScriptType.INTEGER), Set.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.set(ScriptType.INTEGER), Set.of(text("wrong"))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(map, new Object()));
    }
    @Test void hashTypesAndCanonicalShapesAreStrict() {
        for (var type : List.of(ScriptType.INTEGER, ScriptType.TEXT, ScriptType.BOOLEAN, ScriptType.UUID, ScriptType.LOCATION)) {
            assertTrue(type.isHashable()); assertTrue(ScriptType.set(type).isSet()); assertTrue(ScriptType.map(type, ScriptType.UNIT).isMap());
        }
        for (var type : List.of(ScriptType.UNIT, ScriptType.optional(ScriptType.TEXT), ScriptType.list(ScriptType.TEXT), ScriptType.set(ScriptType.TEXT), ScriptType.map(ScriptType.TEXT, ScriptType.TEXT))) {
            assertFalse(type.isHashable());
            assertThrows(IllegalArgumentException.class, () -> ScriptType.set(type));
            assertThrows(IllegalArgumentException.class, () -> ScriptType.map(type, ScriptType.TEXT));
        }
        assertEquals("MAP<TEXT,INTEGER>", ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER).name());
        assertEquals("SET<INTEGER>", ScriptType.set(ScriptType.INTEGER).name());
        assertEquals(ScriptType.TEXT, ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER).key());
        assertNull(ScriptType.set(ScriptType.TEXT).key());
        assertThrows(IllegalArgumentException.class, () -> new ScriptType("MAP<TEXT,INTEGER>", ScriptType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> new ScriptType("INTEGER", null, ScriptType.TEXT));
        assertThrows(IllegalArgumentException.class, () -> new ScriptType("LIST<TEXT>", ScriptType.TEXT, ScriptType.TEXT));
        assertThrows(IllegalArgumentException.class, () -> new ScriptType("SET", null));
    }
    @Test void boundsCoverNestedMapValuesAndSetCardinality() {
        var map = new LinkedHashMap<ScriptValue, ScriptValue>(); var set = new LinkedHashSet<ScriptValue>();
        for (int i = 0; i < 256; i++) { map.put(integer(i), text("x")); set.add(integer(i)); }
        assertEquals(256, new ScriptValue(ScriptType.map(ScriptType.INTEGER, ScriptType.TEXT), map).map().size());
        assertEquals(256, new ScriptValue(ScriptType.set(ScriptType.INTEGER), set).set().size());
        map.put(integer(256), text("x")); set.add(integer(256));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.map(ScriptType.INTEGER, ScriptType.TEXT), map));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue(ScriptType.set(ScriptType.INTEGER), set));
        ScriptType nested = ScriptType.set(ScriptType.TEXT);
        for (int i = 1; i < 8; i++) nested = ScriptType.map(ScriptType.INTEGER, nested);
        ScriptType limit = nested;
        assertThrows(IllegalArgumentException.class, () -> ScriptType.optional(limit));
        var inner = new ScriptValue(ScriptType.set(ScriptType.TEXT), Set.of(text("a")));
        var outer = new ScriptValue(ScriptType.map(ScriptType.INTEGER, inner.type()), Map.of(integer(1), inner));
        assertEquals(inner, outer.map().get(integer(1)));
    }
    @Test void equivalentScalarKeysHaveStableEqualityAndHashing() {
        UUID id = UUID.randomUUID();
        var location = new ScriptValue.LocationSnapshot(id, 1, 2, 3, 4, 5);
        for (var value : List.of(integer(42), text("abc"), new ScriptValue(ScriptType.BOOLEAN, true), new ScriptValue(ScriptType.UUID, id), new ScriptValue(ScriptType.LOCATION, location))) {
            var copy = new ScriptValue(value.type(), value.value());
            assertEquals(value, copy); assertEquals(value.hashCode(), copy.hashCode());
            assertEquals(1, new ScriptValue(ScriptType.set(value.type()), new LinkedHashSet<>(List.of(value, copy))).set().size());
            assertEquals(text("found"), new ScriptValue(ScriptType.map(value.type(), ScriptType.TEXT), Map.of(value, text("found"))).map().get(copy));
        }
    }
}
