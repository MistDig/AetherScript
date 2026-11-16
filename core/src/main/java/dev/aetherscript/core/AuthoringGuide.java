package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.io.IOException;
import java.io.InputStream;

/** Offline language contract shared by the CLI and MCP discovery. */
public final class AuthoringGuide {
    private static final JsonNode GUIDE = load();

    private AuthoringGuide() {}

    public static JsonNode metadata() {
        ObjectNode guide = GUIDE.deepCopy();
        var calls = guide.putArray("hostCallables");
        for (var descriptor : StandardCallables.create().descriptors()) {
            var call = calls.addObject();
            call.put("id", descriptor.id());
            call.put("name", descriptor.dslName());
            var parameters = call.putArray("parameters");
            descriptor.parameters().forEach(type -> parameters.add(type.name().toLowerCase(Locale.ROOT)));
            call.put("returns", descriptor.result().name().toLowerCase(Locale.ROOT));
            call.put("executionContext", descriptor.context().name());
            var capabilities = call.putArray("requiredCapabilities");
            descriptor.requiredCapabilities().stream().sorted().forEach(capabilities::add);
            call.put("playerRequired", descriptor.playerRequired());
            call.put("platform", descriptor.platform());
            call.put("owner", descriptor.owner());
            call.put("errors", descriptor.errorContract());
            call.put("description", descriptor.documentation());
        }
        guide.put("hostAuthorization", "Host calls require a real player and console-approved per-feature capabilities. Existing MCP connections remain message-only: native script apply and live test are denied even with all-script access. Offline validation can type-check host calls, but offline execution requires a host context.");
        guide.put("hostEffects", "Host programs stream effects in source order. Cancellation stops future effects; already committed messages, state and teleports are not rolled back. Invocations belong to the active revision and player session.");
        return guide;
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
