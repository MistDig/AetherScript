package dev.aetherscript.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Local-only flat source packages. Saved snapshots never consult the filesystem. */
public final class AetherPackage {
    public static final int MAX_FILES = 32;
    // JSON escaping can expand source bytes by six; the decoded/composed limit remains MAX_BYTES.
    public static final int MAX_SNAPSHOT_BYTES = FeatureParser.MAX_BYTES * 6 + 4096;
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}\\.aether");
    private static final Pattern IMPORT = Pattern.compile("import \\\"([a-z][a-z0-9-]{0,63}\\.aether)\\\"[ \\t]*(?://.*)?");
    private AetherPackage() {}
    public static SourceFile read(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) throw new IOException("Unsafe package directory");
        TreeMap<String, String> files = new TreeMap<>();
        int total = 0;
        try (var paths = Files.list(root)) {
            for (Path path : paths.limit(MAX_FILES + 1).sorted().toList()) {
                String name = path.getFileName().toString();
                if (files.size() >= MAX_FILES) throw fail(name, 1, "Package exceeds 32 files");
                if (!NAME.matcher(name).matches() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().getParent().equals(root))
                    throw fail(name, 1, "Only flat regular .aether files are allowed; links and subdirectories are forbidden");
                byte[] bytes;
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(FeatureParser.MAX_BYTES + 1); }
                total += bytes.length;
                if (total > FeatureParser.MAX_BYTES) throw fail(name, 1, "Package source exceeds 65536 bytes");
                files.put(name, decode(bytes, name));
            }
        }
        byte[] snapshot = FeatureParser.JSON.writeValueAsBytes(files);
        return new SourceFile(root.getFileName().toString(), "aetherpack", snapshot);
    }
    public static CompiledFeature parse(byte[] snapshot, CallableRegistry callables) {
        if (snapshot.length > MAX_SNAPSHOT_BYTES) throw fail("main.aether", 1, "Package snapshot too large");
        TreeMap<String, String> files = new TreeMap<>();
        try (var parser = FeatureParser.JSON.createParser(snapshot)) {
            com.fasterxml.jackson.databind.JsonNode node = FeatureParser.JSON.readTree(parser);
            if (parser.nextToken() != null || node == null || !node.isObject() || node.size() > MAX_FILES) throw fail("main.aether", 1, "Invalid package snapshot");
            int total = 0;
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!NAME.matcher(field.getKey()).matches() || !field.getValue().isTextual()) throw fail("main.aether", 1, "Invalid package module");
                String text = field.getValue().textValue();
                total += text.getBytes(StandardCharsets.UTF_8).length;
                if (total > FeatureParser.MAX_BYTES) throw fail(field.getKey(), 1, "Package source exceeds 65536 bytes");
                files.put(field.getKey(), text);
            }
        } catch (ValidationException error) { throw error; }
        catch (IOException error) { throw fail("main.aether", 1, "Invalid package snapshot JSON"); }
        if (!files.containsKey("main.aether")) throw fail("main.aether", 1, "Package requires main.aether");
        List<Line> dependencyLines = new ArrayList<>(), entryLines = new ArrayList<>();
        Set<String> visited = new HashSet<>(), visiting = new HashSet<>();
        visit("main.aether", files, visited, visiting, dependencyLines, entryLines);
        if (visited.size() != files.size()) throw fail(files.keySet().stream().filter(n -> !visited.contains(n)).findFirst().orElseThrow(), 1, "Module is not imported from main.aether");
        // The entry header precedes all imported declarations, preserving the existing frontend grammar.
        int header = 0;
        while (header < entryLines.size() && (entryLines.get(header).text().isBlank() || entryLines.get(header).text().stripLeading().startsWith("//"))) header++;
        if (header == entryLines.size() || !entryLines.get(header).text().stripLeading().startsWith("feature ")) throw fail("main.aether", header + 1, "Entry requires a feature header on one line");
        List<Line> lines = new ArrayList<>(entryLines.subList(0, header + 1));
        lines.addAll(dependencyLines); lines.addAll(entryLines.subList(header + 1, entryLines.size()));
        StringBuilder composed = new StringBuilder();
        for (Line line : lines) composed.append(line.text()).append('\n');
        byte[] bytes = composed.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > FeatureParser.MAX_BYTES) throw fail("main.aether", 1, "Composed package exceeds 65536 bytes");
        try {
            var compiled = new TextFrontend(callables).parse(bytes);
            return new CompiledFeature(compiled.definition(), FeatureParser.hash(snapshot), compiled.irHash(), compiled.program());
        } catch (ValidationException error) {
            Line origin = lines.get(Math.max(0, Math.min(lines.size() - 1, error.line() - 1)));
            throw new ValidationException(origin.file(), error.reason(), origin.number(), error.column());
        }
    }
    private record Line(String file, int number, String text) {}
    private static void visit(String name, Map<String,String> files, Set<String> visited, Set<String> visiting, List<Line> dependencies, List<Line> entry) {
        if (visiting.contains(name)) throw fail(name, 1, "Import cycle detected");
        if (visited.contains(name)) return;
        if (!files.containsKey(name)) throw fail(name, 1, "Imported module is missing");
        visiting.add(name);
        String[] raw = files.get(name).split("\\n", -1);
        List<Line> body = new ArrayList<>(); Set<String> imports = new HashSet<>(); boolean leading = true;
        for (int i = 0; i < raw.length; i++) {
            String text = raw[i].replace("\r", ""), trimmed = text.strip();
            if (trimmed.startsWith("import") && (trimmed.length() == 6 || Character.isWhitespace(trimmed.charAt(6)))) {
                Matcher match = IMPORT.matcher(trimmed);
                if (!leading || !match.matches()) throw fail(name, i + 1, "Use leading import \"module.aether\" with a flat local filename");
                String dependency = match.group(1);
                if (!imports.add(dependency)) throw fail(name, i + 1, "Duplicate import: " + dependency);
                if (!files.containsKey(dependency)) throw fail(name, i + 1, "Imported module is missing: " + dependency);
                if (visiting.contains(dependency)) throw fail(name, i + 1, "Import cycle detected: " + dependency);
                visit(dependency, files, visited, visiting, dependencies, entry);
                continue;
            }
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) leading = false;
            body.add(new Line(name, i + 1, text));
        }
        if (name.equals("main.aether")) entry.addAll(body);
        else { validateModule(body); dependencies.addAll(body); }
        visiting.remove(name); visited.add(name);
    }
    private static void validateModule(List<Line> lines) {
        int depth = 0; boolean string = false, escape = false;
        for (Line line : lines) {
            String text = line.text().stripLeading();
            if (depth == 0 && !text.isBlank() && !text.startsWith("//") && !text.startsWith("fn ") && !text.startsWith("record "))
                throw fail(line.file(), line.number(), "Modules may contain only function and record declarations");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (!string && c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') break;
                if (string) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') string = false; }
                else if (c == '"') string = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String tail = text.substring(i + 1).strip();
                        if (!tail.isEmpty() && !tail.startsWith("//")) throw fail(line.file(), line.number(), "Put each declaration on its own line");
                    }
                }
            }
        }
    }
    private static String decode(byte[] bytes, String name) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException error) { throw fail(name, 1, "Invalid UTF-8 encoding"); }
    }
    private static ValidationException fail(String file, int line, String reason) { return new ValidationException(file, reason, line, 1); }
}
