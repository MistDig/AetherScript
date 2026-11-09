package dev.aetherscript.core;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Local owner-edited source directory. Gateway never has this object. */
public final class ScriptStore {
    private final Path root;
    private final FeatureParser parser = new FeatureParser();
    public ScriptStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Map<String, CompiledFeature> read(String selected) throws IOException {
        if (selected != null) FeatureParser.requireId(selected);
        if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root)) throw new IOException("Unsafe scripts directory");
        Map<String, CompiledFeature> result = new TreeMap<>();
        try (var paths = Files.list(root)) {
            List<Path> files = paths.limit(257).toList();
            if (files.size() > 256) throw new IOException("Source directory exceeds 256 entries");
            for (Path file : files) {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot < 0) continue;
                String format = name.substring(dot + 1);
                if (!Set.of("yml", "yaml", "json").contains(format)) continue;
                String id = name.substring(0, dot);
                if (selected != null && !selected.equals(id)) continue;
                FeatureParser.requireId(id);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().getParent().equals(root)) throw new IOException("Unsafe script file");
                byte[] bytes;
                try (InputStream stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes(FeatureParser.MAX_BYTES + 1); }
                CompiledFeature feature = parser.parse(bytes, format);
                if (!id.equals(feature.definition().id())) throw new ValidationException("$.id", "ID must match filename");
                if (result.putIfAbsent(id, feature) != null) throw new ValidationException("$.id", "duplicate feature ID");
            }
        }
        if (selected != null && result.isEmpty()) throw new ValidationException("$.id", "feature source not found");
        return Map.copyOf(result);
    }
}
