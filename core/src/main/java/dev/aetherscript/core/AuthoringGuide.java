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
            if (CallableRegistry.typedState(descriptor)) {
                parameters.removeAll();
                if (descriptor.id().equals("state.shared.read")) {
                    parameters.add("text"); parameters.add("text"); parameters.add("T");
                    call.put("returns", "T");
                    call.put("typeParameter", "T inferred from argument 3; unit and containers or records containing unit are unsupported");
                } else if (descriptor.id().equals("state.shared.commit")) {
                    parameters.add("text"); parameters.add("map<text,T>"); parameters.add("list<text>");
                    call.put("returns", "boolean");
                    call.put("typeParameter", "T inferred from map values in argument 2; unit and containers or records containing unit are unsupported");
                } else {
                    parameters.add("text"); parameters.add("T");
                    call.put("returns", descriptor.id().equals("state.own.load") ? "T" : "unit");
                    call.put("typeParameter", "T inferred from argument 2; unit and containers containing unit are unsupported");
                }
            }
            call.put("executionContext", descriptor.context().name());
            var capabilities = call.putArray("requiredCapabilities");
            descriptor.requiredCapabilities().stream().sorted().forEach(capabilities::add);
            call.put("playerRequired", descriptor.playerRequired());
            call.put("platform", descriptor.platform());
            call.put("owner", descriptor.owner());
            call.put("errors", descriptor.errorContract());
            call.put("description", descriptor.documentation());
        }
        guide.put("hostAuthorization", "Fresh browser grants can approve the supported native capabilities across all or selected feature IDs. Existing legacy connections remain message-only until reapproved. Native apply persists the owner's per-feature execution approval; explicit local ais permit/restrict ceilings take precedence. Live tests use only the real online player chosen in the browser and enforce that player's command permission. Offline validation can type-check host calls; offline host execution remains unavailable.");
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
