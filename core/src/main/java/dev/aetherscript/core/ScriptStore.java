package dev.aetherscript.core;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Always one owner-editable source per ID. Internal revisions are never exposed as edit targets. */
public final class ScriptStore {
    public record Inspection(String id, String fileName, SourceFile source, CompiledFeature compiled, ValidationException error) {
        public boolean valid() { return error == null && compiled != null; }
    }
    private final Path root;
    public ScriptStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Map<String, Inspection> inspect(String selected) throws IOException {
        if (selected != null) FeatureParser.requireId(selected);
        if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root)) throw new IOException("Unsafe scripts directory");
        Map<String, Inspection> result = new TreeMap<>();
        try (var paths = Files.list(root)) {
            List<Path> files = paths.limit(257).sorted().toList();
            if (files.size() > 256) throw new IOException("Source directory exceeds 256 entries");
            for (Path file : files) {
                String name = file.getFileName().toString(); int dot = name.lastIndexOf('.');
                if (dot < 0) continue;
                String format = name.substring(dot + 1), id = name.substring(0, dot);
                if (!Set.of("yml", "yaml", "json", "aether").contains(format) || (selected != null && !selected.equals(id))) continue;
                SourceFile source = null;
                try {
                    FeatureParser.requireId(id);
                    if (result.containsKey(id)) {
                        result.put(id, new Inspection(id, result.get(id).fileName() + " + " + name, null, null,
                            new ValidationException("$.id", "Duplicate feature ID. Keep one editable .yml, .yaml, .json, or .aether file for this ID.")));
                        continue;
                    }
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().getParent().equals(root)) throw new IOException("Unsafe source file");
                    byte[] bytes;
                    try (InputStream stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes(FeatureParser.MAX_BYTES + 1); }
                    source = new SourceFile(name, format, bytes);
                    CompiledFeature compiled = source.compile();
                    if (!id.equals(compiled.definition().id())) throw new ValidationException("$.id", "ID must match filename: " + id);
                    result.put(id, new Inspection(id, name, source, compiled, null));
                } catch (ValidationException e) { result.put(id, new Inspection(id, name, source, null, e)); }
                catch (IOException e) { result.put(id, new Inspection(id, name, null, null, new ValidationException("$", "Source file cannot be read safely. Check that it is a regular file inside scripts/."))); }
            }
        }
        if (selected != null && result.isEmpty()) result.put(selected, new Inspection(selected, selected + ".yml/.yaml/.json/.aether", null, null,
            new ValidationException("$", "Editable file is missing. Restore it in scripts/ or use the existing saved version.")));
        return Collections.unmodifiableMap(result);
    }
    public Map<String, CompiledFeature> read(String selected) throws IOException {
        Map<String, CompiledFeature> result = new TreeMap<>();
        for (Inspection inspection : inspect(selected).values()) {
            if (!inspection.valid()) throw inspection.error();
            result.put(inspection.id(), inspection.compiled());
        }
        return Map.copyOf(result);
    }
}
