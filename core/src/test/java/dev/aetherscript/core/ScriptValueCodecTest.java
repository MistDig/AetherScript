package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptValueCodecTest {
    private static ScriptValue integer(long n) { return new ScriptValue(ScriptType.INTEGER, n); }
    private static ScriptValue text(String s) { return new ScriptValue(ScriptType.TEXT, s); }
    private static void roundTrip(ScriptValue value) { assertEquals(value, ScriptValueCodec.decode(ScriptValueCodec.encode(value))); }

    @Test void stableVersionOneWireTags() {
        var fixtures = new LinkedHashMap<ScriptValue, String>();
        fixtures.put(integer(1), "000000000000000001");
        fixtures.put(text("a"), "010000000161");
        fixtures.put(new ScriptValue(ScriptType.BOOLEAN, true), "0201");
        fixtures.put(new ScriptValue(ScriptType.list(ScriptType.INTEGER), List.of()), "03000000");
        fixtures.put(new ScriptValue(ScriptType.UUID, new UUID(0, 0)), "0400000000000000000000000000000000");
        fixtures.put(new ScriptValue(ScriptType.LOCATION, new ScriptValue.LocationSnapshot(new UUID(0, 0), 0, 0, 0, 0, 0)), "05" + "00".repeat(48));
        fixtures.put(new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE), "06");
        fixtures.put(new ScriptValue(ScriptType.optional(ScriptType.TEXT), Optional.empty()), "070100");
        fixtures.put(new ScriptValue(ScriptType.map(ScriptType.TEXT, ScriptType.INTEGER), Map.of()), "0801000000");
        fixtures.put(new ScriptValue(ScriptType.set(ScriptType.TEXT), Set.of()), "09010000");
        fixtures.put(new ScriptValue(ScriptType.ITEM, new ScriptValue.ItemDescriptor("bread", 16)), "0a000f6d696e6563726166743a627265616400000010");
        fixtures.forEach((value, hex) -> {
            String wire = "ASV1:" + Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hex));
            assertEquals(wire, ScriptValueCodec.encode(value));
            assertEquals(value, ScriptValueCodec.decode(wire));
        });
    }
    @Test void itemModelRoundTripsBesideVanillaAndExistingCustomItems() {
        roundTrip(new ScriptValue(ScriptType.ITEM, new ScriptValue.ItemDescriptor("paper", 2)));
        roundTrip(new ScriptValue(ScriptType.ITEM, new ScriptValue.ItemDescriptor("paper", 2, "test", "token", "Token")));
        roundTrip(new ScriptValue(ScriptType.ITEM, new ScriptValue.ItemDescriptor("paper", 2, "test", "token", "Token", "test:tokens/cloud")));
        assertThrows(IllegalArgumentException.class, () -> new ScriptValue.ItemDescriptor("paper", 2, "test", "token", "Token", "test:../token"));
    }

    @Test void roundTripsClosedTypesAndEmptyContainers() {
        for (var value : List.of(integer(Long.MIN_VALUE), integer(Long.MAX_VALUE), text("hello\n\u0000雪😀"), new ScriptValue(ScriptType.BOOLEAN, true),
            new ScriptValue(ScriptType.UUID, UUID.randomUUID()), new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE),
            new ScriptValue(ScriptType.LOCATION, new ScriptValue.LocationSnapshot(UUID.randomUUID(), -0.0, 2.5, -3, 45, -90)),
            new ScriptValue(ScriptType.optional(ScriptType.TEXT), Optional.empty()),
            new ScriptValue(ScriptType.optional(ScriptType.INTEGER), Optional.of(integer(17))),
            new ScriptValue(ScriptType.list(ScriptType.TEXT), List.of()),
            new ScriptValue(ScriptType.map(ScriptType.UUID, ScriptType.list(ScriptType.INTEGER)), Map.of()),
            new ScriptValue(ScriptType.set(ScriptType.LOCATION), Set.of()))) roundTrip(value);
    }
    @Test void preservesMapAndSetInsertionOrderAndNestedTypes() {
        var map = new LinkedHashMap<ScriptValue, ScriptValue>();
        map.put(text("z"), new ScriptValue(ScriptType.list(ScriptType.INTEGER), List.of(integer(3), integer(1))));
        map.put(text("a"), new ScriptValue(ScriptType.list(ScriptType.INTEGER), List.of()));
        var original = new ScriptValue(ScriptType.map(ScriptType.TEXT, ScriptType.list(ScriptType.INTEGER)), map);
        var decoded = ScriptValueCodec.decode(ScriptValueCodec.encode(original));
        assertEquals(original, decoded); assertEquals(List.copyOf(map.keySet()), List.copyOf(decoded.map().keySet()));
        var set = new ScriptValue(ScriptType.set(ScriptType.INTEGER), new LinkedHashSet<>(List.of(integer(9), integer(2), integer(-1))));
        assertEquals(List.copyOf(set.set()), List.copyOf(ScriptValueCodec.decode(ScriptValueCodec.encode(set)).set()));
    }
    @Test void enforcesEncodedByteAndWorkBounds() {
        roundTrip(text("x".repeat(2048)));
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.encode(text("雪".repeat(2048))));
        ScriptValue value = new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE);
        for (int i = 0; i < 3; i++) value = new ScriptValue(ScriptType.list(value.type()), Collections.nCopies(256, value));
        ScriptValue huge = value;
        byte[] unitTree = new byte[39]; unitTree[0] = 3; unitTree[1] = 3; unitTree[2] = 6; unitTree[4] = 17;
        for (int i = 5; i < unitTree.length; i += 2) unitTree[i] = 1;
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.decode("ASV1:" + Base64.getEncoder().encodeToString(unitTree)));
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.encode(huge));
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.decode("ASV1:" + "A".repeat(4096)));
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.encode(text("\ud800")));
    }
    @Test void rejectsMalformedTruncatedDuplicateAndTrailingData() {
        for (String input : List.of("", "ASV2:AA==", "ASV1:!", "ASV1:/w==", "ASV1:AgI=", "ASV1:AA==", "ASV1:BgA=", "ASV1:AQAAAAHA", "ASV1:AA"))
            assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.decode(input), input);
        // SET<BOOLEAN> with two equal elements; MAP<BOOLEAN,UNIT> with duplicate keys.
        for (byte[] bytes : List.of(new byte[] {9, 2, 0, 2, 1, 1}, new byte[] {8, 2, 6, 0, 2, 1, 1}))
            assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.decode("ASV1:" + Base64.getEncoder().encodeToString(bytes)));
        String encoded = ScriptValueCodec.encode(integer(1));
        byte[] bytes = Base64.getDecoder().decode(encoded.substring(5));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(IllegalArgumentException.class, () -> ScriptValueCodec.decode("ASV1:" + Base64.getEncoder().encodeToString(trailing)));
    }
}
