package dev.aetherscript.cli;

import dev.aetherscript.core.AuthoringGuide;
import dev.aetherscript.core.AetherPackage;
import dev.aetherscript.core.CompiledFeature;
import dev.aetherscript.core.FeatureParser;
import dev.aetherscript.core.ScriptStore;
import dev.aetherscript.core.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
        if (args.length == 2 && args[0].equals("--package")) {
            validatePackage(Path.of(args[1]).toAbsolutePath().normalize());
            return;
        }
        if (args.length != 1 || !Set.of("aether", "yaml", "json", "aetherpack").contains(args[0])) {
            print(Map.of("valid", false, "reason", "Supply aether, yaml, json or aetherpack on stdin, pass --package <directory.aetherpack>, or pass --language."));
            System.exit(2);
        }
        int limit = args[0].equals("aetherpack") ? AetherPackage.MAX_SNAPSHOT_BYTES : FeatureParser.MAX_BYTES;
        byte[] source = System.in.readNBytes(limit + 1);
        try {
            success(new FeatureParser().parse(source, args[0]));
        } catch (ValidationException error) {
            failure(error);
        }
    }

    private static void validatePackage(Path directory) throws IOException {
        String suffix = ".aetherpack";
        String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
        try {
            if (!name.endsWith(suffix) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw new ValidationException("$", "Expected a local directory named <feature>.aetherpack");
            String id = name.substring(0, name.length() - suffix.length());
            var inspection = new ScriptStore(directory.getParent()).inspect(id).get(id);
            if (inspection == null) throw new ValidationException("$", "Package directory was not found");
            if (!inspection.valid()) throw inspection.error();
            if (!inspection.fileName().equals(name)) throw new ValidationException("$", "Expected package source");
            success(inspection.compiled());
        } catch (ValidationException error) {
            failure(error);
        } catch (IOException error) {
            failure(new ValidationException("$", "Package directory could not be read safely"));
        }
    }

    private static void success(CompiledFeature feature) throws IOException {
        print(Map.of("valid", true, "featureId", feature.definition().id(),
            "sourceHash", feature.sourceHash(), "irHash", feature.irHash(),
            "requiredCapabilities", feature.definition().requires()));
    }

    private static void failure(ValidationException error) throws IOException {
        print(Map.of("valid", false, "code", "VALIDATION_FAILED", "path", error.path(),
            "line", error.line(), "column", error.column(), "reason", error.reason()));
        System.exit(1);
    }

    private static void print(Object value) throws IOException {
        System.out.write(FeatureParser.JSON.writeValueAsBytes(value));
        System.out.write('\n');
        System.out.flush();
    }
}
