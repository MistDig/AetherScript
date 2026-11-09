package dev.aetherscript.core;

import java.io.IOException;
import java.util.Properties;

/** Version generated from the single release version in gradle.properties. */
public final class BuildInfo {
    public static final String VERSION = loadVersion();
    private BuildInfo() {}
    private static String loadVersion() {
        Properties properties = new Properties();
        try (var input = BuildInfo.class.getResourceAsStream("/aetherscript-build.properties")) {
            if (input == null) throw new IllegalStateException("Missing build version");
            properties.load(input);
            String version = properties.getProperty("version");
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalStateException("Invalid build version");
            return version;
        } catch (IOException e) { throw new IllegalStateException("Cannot read build version", e); }
    }
}
