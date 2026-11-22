package dev.aetherscript.core;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Always one owner-editable source per ID. Internal revisions are never exposed as edit targets. */
public final class ScriptStore {
    public record Inspection(String id, String fileName, SourceFile source, CompiledFeature compiled, ValidationException error) {
        public boolean valid() { return error == null && compiled != null; }
    }
    private static final com.fasterxml.jackson.databind.ObjectMapper INTENT_JSON = intentJson();
    private static com.fasterxml.jackson.databind.ObjectMapper intentJson() {
        var json = FeatureParser.JSON.copy();
        json.getFactory().setStreamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(FeatureParser.MAX_DEPTH).maxStringLength(AetherPackage.MAX_SNAPSHOT_BYTES).maxNumberLength(64).build());
        return json;
    }
    private static final int MAX_INTENT_BYTES = AetherPackage.MAX_SNAPSHOT_BYTES * 6 + 4096;
    private final Path root;
    private final java.util.function.Supplier<FeatureParser> parsers;
    public record RemoteIntent(String id, String expectedHash, String source, long baseSequence, String format) {
        public RemoteIntent(String id, String expectedHash, String source, long baseSequence) { this(id, expectedHash, source, baseSequence, "aether"); }
        public RemoteIntent { if (format == null) format = "aether"; }
    }
    public ScriptStore(Path root) { this(root, FeatureParser::new); }
    public ScriptStore(Path root, java.util.function.Supplier<FeatureParser> parsers) {
        this.root = root.toAbsolutePath().normalize(); this.parsers = Objects.requireNonNull(parsers, "parsers");
    }
    public FeatureParser parser() { return Objects.requireNonNull(parsers.get(), "parser supplier result"); }
    public CompiledFeature compile(SourceFile source) { return source.compile(parser()); }
    private Path intents() throws IOException {
        if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root)) throw new IOException("Unsafe scripts directory");
        Path directory = root.resolve(".remote-intents");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !directory.toRealPath().equals(directory)) throw new IOException("Unsafe intent directory");
        return directory;
    }
    /** Persist the candidate before touching the active registry or editable source. */
    public synchronized void beginRemoteIntent(String id, String expectedHash, String text, long baseSequence) throws IOException {
        beginRemoteIntent(id, expectedHash, "aether", text, baseSequence);
    }
    public synchronized void beginRemoteIntent(String id, String expectedHash, String format, String text, long baseSequence) throws IOException {
        SourceFile candidate = SourceFile.authored(id, format, text, parser());
        SourceFile current = editableSource(id, format);
        if (!Objects.equals(expectedHash, current == null ? "ABSENT" : current.hash())) throw new IOException("STALE_SOURCE");
        Path directory = intents(), target = directory.resolve(id + ".json");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("REMOTE_RECOVERY_PENDING");
        byte[] bytes = INTENT_JSON.writeValueAsBytes(new RemoteIntent(id, expectedHash, candidate.text(), baseSequence, format));
        Path temporary = Files.createTempFile(directory, ".intent-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            flushDirectory(directory);
        } finally { Files.deleteIfExists(temporary); }
    }
    public synchronized void finishRemoteIntent(String id) throws IOException {
        FeatureParser.requireId(id); Path directory = intents(); Files.deleteIfExists(directory.resolve(id + ".json")); flushDirectory(directory);
    }
    /** Durable activation decides recovery; unrelated owner source changes always win. */
    public synchronized void recoverRemoteIntents(RevisionStore store) throws IOException {
        FeatureParser parser = parser();
        Path directory = intents();
        try (var paths = Files.list(directory)) {
            var files = paths.limit(129).toList();
            if (files.size() > 128) throw new IOException("Too many remote recovery intents");
            for (Path path : files) {
                if (!path.getFileName().toString().endsWith(".json")) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe remote recovery intent");
                byte[] bytes;
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(MAX_INTENT_BYTES + 1); }
                if (bytes.length > MAX_INTENT_BYTES) throw new IOException("Remote recovery intent too large");
                RemoteIntent intent = INTENT_JSON.readValue(bytes, RemoteIntent.class);
                FeatureParser.requireId(intent.id());
                if (!path.getFileName().toString().equals(intent.id() + ".json")) throw new IOException("Remote intent ID mismatch");
                var candidate = SourceFile.authored(intent.id(), intent.format(), intent.source(), parser);
                if (!candidate.compile(parser).definition().id().equals(intent.id())) throw new IOException("Remote intent source mismatch");
                var history = store.state().features().get(intent.id());
                if (store.state().sequence() > intent.baseSequence() && history != null && history.activeRevision() != null && history.activeRevision().hash().equals(candidate.hash()) && history.activeRevision().format().equals(intent.format())) {
                    try {
                        SourceFile current = editableSource(intent.id(), intent.format());
                        if (Objects.equals(intent.expectedHash(), current == null ? "ABSENT" : current.hash())) compareAndSwapSource(intent.id(), intent.expectedHash(), intent.format(), intent.source());
                    } catch (IOException unsafeOwnerSource) { /* Owner format/directory changes win over journal recovery. */ }
                }
                Files.delete(path);
            }
        }
        flushDirectory(directory);
    }
    private static void flushDirectory(Path directory) {
        // Windows does not expose directory fsync through FileChannel. File contents are forced.
        try (var channel = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        catch (IOException | UnsupportedOperationException ignored) { }
    }
    /** Old text API remains deliberately format-restricted. */
    public synchronized SourceFile textSource(String id) throws IOException { return editableSource(id, "aether"); }
    public synchronized SourceFile editableSource(String id, String format) throws IOException {
        return editableSource(id, format, parser());
    }
    synchronized SourceFile editableSource(String id, String format, FeatureParser parser) throws IOException {
        FeatureParser.requireId(id);
        if (!Set.of("aether", "aetherpack").contains(format)) throw new IOException("WRITABLE_FORMAT_REQUIRED");
        var file = inspect(null, parser).get(id);
        if (file == null) return null;
        if (!file.fileName().equals(id + "." + format)) throw new IOException(format.equals("aether") ? "TEXT_SOURCE_REQUIRED" : "SOURCE_FORMAT_MISMATCH");
        Path path = root.resolve(file.fileName());
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("PACKAGE_DIRECTORY_READ_ONLY");
        if (file.source() == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("EDITABLE_SOURCE_UNSAFE");
        return file.source();
    }
    public synchronized SourceFile compareAndSwapText(String id, String expectedHash, String text) throws IOException { return compareAndSwapSource(id, expectedHash, "aether", text); }
    public synchronized SourceFile compareAndSwapSource(String id, String expectedHash, String format, String text) throws IOException {
        SourceFile next = SourceFile.authored(id, format, text, parser());
        SourceFile previous = editableSource(id, format);
        if (!Objects.equals(expectedHash, previous == null ? "ABSENT" : previous.hash())) throw new IOException("STALE_SOURCE");
        if (previous != null && previous.hash().equals(next.hash())) return previous;
        Path temporary = Files.createTempFile(root, ".remote-", ".tmp");
        try {
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = java.nio.ByteBuffer.wrap(next.bytes());
                while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
            }
            SourceFile checked = editableSource(id, format);
            if (!Objects.equals(expectedHash, checked == null ? "ABSENT" : checked.hash())) throw new IOException("STALE_SOURCE");
            Files.move(temporary, root.resolve(next.fileName()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            flushDirectory(root);
            return next;
        } finally { Files.deleteIfExists(temporary); }
    }
    public Map<String, Inspection> inspect(String selected) throws IOException { return inspect(selected, parser()); }
    public Map<String, Inspection> inspect(String selected, FeatureParser parser) throws IOException {
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
                if (!Set.of("yml", "yaml", "json", "aether", "aetherpack").contains(format) || (selected != null && !selected.equals(id))) continue;
                SourceFile source = null;
                try {
                    FeatureParser.requireId(id);
                    if (result.containsKey(id)) {
                        result.put(id, new Inspection(id, result.get(id).fileName() + " + " + name, null, null,
                            new ValidationException("$.id", "Duplicate feature ID. Keep one editable .yml, .yaml, .json, or .aether file or .aetherpack directory for this ID.")));
                        continue;
                    }
                    if (format.equals("aetherpack") && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                        source = AetherPackage.read(file);
                    } else {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().getParent().equals(root)) throw new IOException("Unsafe source file");
                    byte[] bytes;
                    try (InputStream stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes((format.equals("aetherpack") ? AetherPackage.MAX_SNAPSHOT_BYTES : FeatureParser.MAX_BYTES) + 1); }
                    source = new SourceFile(name, format, bytes);
                    }
                    CompiledFeature compiled = source.compile(parser);
                    if (!id.equals(compiled.definition().id())) throw new ValidationException("$.id", "ID must match filename: " + id);
                    result.put(id, new Inspection(id, name, source, compiled, null));
                } catch (ValidationException e) { result.put(id, new Inspection(id, name, source, null, e)); }
                catch (IOException e) { result.put(id, new Inspection(id, name, null, null, new ValidationException("$", "Source file cannot be read safely. Check that it is a regular file inside scripts/."))); }
            }
        }
        if (selected != null && result.isEmpty()) result.put(selected, new Inspection(selected, selected + ".yml/.yaml/.json/.aether/.aetherpack", null, null,
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
