package dev.aetherscript.core;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationCodecTest {
    @Test void stableSnapshotRoundTripsWithoutLiveWorldReferences() throws Exception {
        var value = new ScriptValue.LocationSnapshot(UUID.randomUUID(), -12.5, 70.25, 4, 180, -45);
        assertEquals(value, LocationCodec.decode(LocationCodec.encode(value)));
    }
    @Test void malformedVersionsTypesAndBoundsRejectRatherThanCoerce() throws Exception {
        var location = new ScriptValue.LocationSnapshot(UUID.randomUUID(), 1, 2, 3, 0, 0);
        String source = LocationCodec.encode(location);
        for (String invalid : new String[]{source + " {}", source.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            source.replace("\"x\":1.0", "\"x\":\"1.0\""), source.replace("\"x\":1.0", "\"x\":1e9999")}) {
            assertNotEquals(source, invalid); assertThrows(IOException.class, () -> LocationCodec.decode(invalid));
        }
        assertThrows(IOException.class, () -> LocationCodec.encode(new ScriptValue.LocationSnapshot(UUID.randomUUID(), 30_000_001, 2, 3, 0, 0)));
    }
}
