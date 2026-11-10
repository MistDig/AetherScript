package dev.aetherscript.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.nodes.*;

public record EngineSettings(int retainVersions) {
    public static EngineSettings load(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("config.yml must be a regular file");
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(8193); }
        if (bytes.length > 8192) throw new IOException("config.yml exceeds 8 KiB");
        try {
            LoaderOptions options = new LoaderOptions(); options.setCodePointLimit(8192); options.setNestingDepthLimit(4); options.setMaxAliasesForCollections(0);
            Yaml yaml = new Yaml(options); String source = new String(bytes, StandardCharsets.UTF_8);
            for (Event event : yaml.parse(new StringReader(source))) {
                if (event instanceof AliasEvent || (event instanceof NodeEvent n && n.getAnchor() != null)
                    || (event instanceof ScalarEvent s && s.getTag() != null) || (event instanceof CollectionStartEvent c && c.getTag() != null))
                    throw new IOException("config.yml does not allow tags or aliases");
            }
            Node root = yaml.compose(new StringReader(source));
            if (!(root instanceof MappingNode mapping)) throw new IOException("config.yml needs a mapping");
            Map<String, Integer> values = new HashMap<>();
            for (NodeTuple entry : mapping.getValue()) {
                if (!(entry.getKeyNode() instanceof ScalarNode key) || !key.getTag().equals(Tag.STR)
                    || !(entry.getValueNode() instanceof ScalarNode value) || !value.getTag().equals(Tag.INT)
                    || !value.getValue().matches("[0-9]{1,2}") || values.putIfAbsent(key.getValue(), Integer.parseInt(value.getValue())) != null)
                    throw new IOException("config.yml needs unique integer settings");
            }
            if (!values.keySet().equals(Set.of("configVersion", "retainVersions")) || values.get("configVersion") != 1
                || values.get("retainVersions") < 1 || values.get("retainVersions") > 50)
                throw new IOException("config.yml requires configVersion: 1 and retainVersions: 1..50");
            return new EngineSettings(values.get("retainVersions"));
        } catch (RuntimeException e) { throw new IOException("Invalid config.yml; check syntax, unique keys, and retainVersions: 1..50", e); }
    }
}
