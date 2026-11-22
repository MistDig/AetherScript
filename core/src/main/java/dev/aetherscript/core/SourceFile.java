package dev.aetherscript.core;

import java.nio.charset.StandardCharsets;

public record SourceFile(String fileName, String format, byte[] bytes) {
    public SourceFile { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public String hash() { return FeatureParser.hash(bytes); }
    public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    public CompiledFeature compile() { return compile(new FeatureParser()); }
    public CompiledFeature compile(FeatureParser parser) { return java.util.Objects.requireNonNull(parser, "parser").parse(bytes, format); }
    /** Validate authoring input, then normalize package key ordering without changing module bytes. */
    public static SourceFile authored(String id, String format, String source, FeatureParser parser) {
        FeatureParser.requireId(id);
        if (!java.util.Set.of("aether", "aetherpack").contains(format)) throw new ValidationException("$.format", "Writable format must be aether or aetherpack");
        byte[] bytes = java.util.Objects.requireNonNull(source, "source").getBytes(StandardCharsets.UTF_8);
        int limit = format.equals("aetherpack") ? AetherPackage.MAX_SNAPSHOT_BYTES : FeatureParser.MAX_BYTES;
        if (bytes.length > limit) throw new ValidationException("$.source", "Authoring source exceeds format limit");
        SourceFile candidate = new SourceFile(id + "." + format, format, bytes);
        if (!candidate.compile(parser).definition().id().equals(id)) throw new ValidationException("$.id", "ID must match target");
        if (format.equals("aetherpack")) {
            try {
                var node = FeatureParser.JSON.readTree(bytes); var files = new java.util.TreeMap<String, String>();
                node.fields().forEachRemaining(entry -> files.put(entry.getKey(), entry.getValue().textValue()));
                candidate = new SourceFile(id + ".aetherpack", format, FeatureParser.JSON.writeValueAsBytes(files));
            } catch (java.io.IOException failure) { throw new ValidationException("$.source", "Invalid package snapshot JSON"); }
        }
        return candidate;
    }
}
