package dev.aetherscript.cli;

import dev.aetherscript.core.AuthoringGuide;
import dev.aetherscript.core.FeatureParser;
import dev.aetherscript.core.ValidationException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Offline CLI. Reads one bounded document from stdin and writes a JSON result. */
public final class Validate {
    private Validate() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--language")) {
            print(AuthoringGuide.metadata());
            return;
        }
        if (args.length != 1 || !Set.of("aether", "yaml", "json").contains(args[0])) {
            print(Map.of("valid", false, "reason", "Pass aether, yaml or json and supply the source on stdin, or pass --language."));
            System.exit(2);
        }
        byte[] source = System.in.readNBytes(FeatureParser.MAX_BYTES + 1);
        try {
            var feature = new FeatureParser().parse(source, args[0]);
            print(Map.of("valid", true, "featureId", feature.definition().id(),
                "sourceHash", feature.sourceHash(), "irHash", feature.irHash(),
                "requiredCapabilities", feature.definition().requires()));
        } catch (ValidationException error) {
            print(Map.of("valid", false, "code", "VALIDATION_FAILED", "path", error.path(),
                "line", error.line(), "column", error.column(), "reason", error.reason()));
            System.exit(1);
        }
    }

    private static void print(Object value) throws IOException {
        System.out.println(FeatureParser.JSON.writeValueAsString(value));
    }
}
