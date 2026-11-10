package dev.aetherscript.core;

import java.nio.charset.StandardCharsets;

public record SourceFile(String fileName, String format, byte[] bytes) {
    public SourceFile { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public String hash() { return FeatureParser.hash(bytes); }
    public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    public CompiledFeature compile() { return new FeatureParser().parse(bytes, format); }
}
