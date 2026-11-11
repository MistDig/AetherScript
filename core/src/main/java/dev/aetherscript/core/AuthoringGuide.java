package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;

/** Offline language contract shared by the CLI and MCP discovery. */
public final class AuthoringGuide {
    private static final JsonNode GUIDE = load();

    private AuthoringGuide() {}

    public static JsonNode metadata() {
        return GUIDE.deepCopy();
    }

    private static JsonNode load() {
        try (InputStream input = AuthoringGuide.class.getResourceAsStream("/aetherscript-authoring-v1.json")) {
            if (input == null) throw new IllegalStateException("Missing bundled authoring guide");
            return FeatureParser.JSON.readTree(input);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
