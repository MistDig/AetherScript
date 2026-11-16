package dev.aetherscript.core;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Versioned stable location data; never serializes a live world or player. */
public final class LocationCodec {
    private LocationCodec() {}
    public static String encode(ScriptValue.LocationSnapshot value) throws IOException {
        bounded(value);
        return FeatureParser.JSON.writeValueAsString(Map.of("formatVersion", 1, "world", value.worldId().toString(),
            "x", value.x(), "y", value.y(), "z", value.z(), "yaw", value.yaw(), "pitch", value.pitch()));
    }
    public static ScriptValue.LocationSnapshot decode(String text) throws IOException {
        if (text.length() > FeatureParser.MAX_TEXT) throw new IOException("Location snapshot exceeds limit");
        try (var parser = FeatureParser.JSON.createParser(text)) {
            com.fasterxml.jackson.databind.JsonNode value = FeatureParser.JSON.readTree(parser);
            if (parser.nextToken() != null || value == null || !value.isObject() || value.size() != 7
                || !value.path("formatVersion").isInt() || value.path("formatVersion").intValue() != 1 || !value.path("world").isTextual())
                throw new IOException("Invalid location schema");
            for (String name : new String[]{"x", "y", "z", "yaw", "pitch"})
                if (!value.path(name).isNumber()) throw new IOException("Invalid location coordinate");
            UUID world = UUID.fromString(value.get("world").textValue());
            if (!world.toString().equals(value.get("world").textValue())) throw new IOException("Noncanonical world UUID");
            var result = new ScriptValue.LocationSnapshot(world, value.get("x").doubleValue(), value.get("y").doubleValue(),
                value.get("z").doubleValue(), value.get("yaw").floatValue(), value.get("pitch").floatValue());
            bounded(result); return result;
        } catch (IllegalArgumentException failure) { throw new IOException("Invalid location snapshot", failure); }
    }
    public static void bounded(ScriptValue.LocationSnapshot value) throws IOException {
        if (Math.abs(value.x()) > 30_000_000 || Math.abs(value.z()) > 30_000_000 || Math.abs(value.y()) > 20_000_000)
            throw new IOException("Location outside supported world coordinate bounds");
    }
}
