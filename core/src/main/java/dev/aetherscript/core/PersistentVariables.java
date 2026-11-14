package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Blocking, single-writer durable state. Hosts must call outside the tick/invocation path. */
public final class PersistentVariables implements AutoCloseable {
    public static final int MAX_KEYS = 4096, MAX_BATCH = 128, MAX_TEXT_BYTES = 4096, MAX_FILE_BYTES = 24 * 1024 * 1024;
    public enum Type { TEXT, INTEGER, BOOLEAN }
    public record Value(Type type, String text, Long integer, Boolean bool) {
        public Value {
            Objects.requireNonNull(type);
            if (type == Type.TEXT ? text == null || integer != null || bool != null || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES
                : type == Type.INTEGER ? integer == null || text != null || bool != null : bool == null || text != null || integer != null)
                throw new IllegalArgumentException("Invalid typed variable value");
        }
        public static Value text(String value) { return new Value(Type.TEXT, value, null, null); }
        public static Value integer(long value) { return new Value(Type.INTEGER, null, value, null); }
        public static Value bool(boolean value) { return new Value(Type.BOOLEAN, null, null, value); }
    }
    /** Null player denotes feature scope; player scope uses a canonical UUID. */
    public record Key(String feature, UUID player, String name) {
        public Key { FeatureParser.requireId(feature); if (name == null || !name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid variable name"); }
    }
    public record Entry(long revision, Value value) { public Entry { if (revision < 1) throw new IllegalArgumentException("Invalid revision"); Objects.requireNonNull(value); } }
    public record Mutation(Key key, long expectedRevision, Value value) {
        public Mutation { Objects.requireNonNull(key); Objects.requireNonNull(value); if (expectedRevision < 0) throw new IllegalArgumentException("Invalid expected revision"); }
    }
    public static final class ConflictException extends IllegalStateException {
        public ConflictException(Key key) { super("VARIABLE_CONFLICT: " + key); }
    }
    @FunctionalInterface public interface Faults { void at(String boundary) throws IOException; }
    private final Path root, file;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Faults faults;
    private Map<Key, Entry> state = Map.of();
    private boolean closed, committing, directoryFlushSupported = true;
    public PersistentVariables(Path root) throws IOException { this(root, boundary -> {}); }
    public PersistentVariables(Path root, Faults faults) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.faults = Objects.requireNonNull(faults);
        safeDirectory(); file = this.root.resolve("variables.json");
        Path lockFile = this.root.resolve("variables.lock");
        lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); } catch (IOException | RuntimeException e) { lockChannel.close(); throw new IOException("Variable storage already open or unavailable", e); }
        if (acquired == null) { lockChannel.close(); throw new IOException("Variable storage already open"); }
        lock = acquired;
        try { if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) state = read(); }
        catch (IOException | RuntimeException e) { close(); throw new IOException("STORAGE_CORRUPT: variables.json failed validation; restore owner backup", e); }
    }
    public synchronized Entry get(Key key) { ensureOpen(); return state.get(Objects.requireNonNull(key)); }
    public synchronized Map<Key, Entry> snapshot() { ensureOpen(); return state; }
    public synchronized boolean directoryFlushSupported() { return directoryFlushSupported; }
    /** Revision zero means absent. All keys must be distinct; any conflict rejects the whole batch. */
    public synchronized Map<Key, Entry> commit(List<Mutation> mutations) throws IOException {
        ensureOpen(); if (committing) throw new IllegalStateException("Variable commit already in progress");
        mutations = List.copyOf(mutations);
        if (mutations.isEmpty() || mutations.size() > MAX_BATCH) throw new IllegalArgumentException("Batch must contain 1..128 mutations");
        Map<Key, Entry> next = new HashMap<>(state); Set<Key> seen = new HashSet<>();
        for (Mutation m : mutations) {
            if (!seen.add(m.key())) throw new IllegalArgumentException("Duplicate batch key");
            Entry previous = state.get(m.key()); long revision = previous == null ? 0 : previous.revision();
            if (revision != m.expectedRevision()) throw new ConflictException(m.key());
            next.put(m.key(), new Entry(Math.incrementExact(revision), m.value()));
        }
        if (next.size() > MAX_KEYS) throw new IllegalArgumentException("Variable key limit");
        Map<Key, Entry> committed = Map.copyOf(next); byte[] bytes = encode(committed);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Variable storage size limit");
        committing = true;
        try { write(bytes, committed); return state; } finally { committing = false; }
    }
    private byte[] encode(Map<Key, Entry> values) throws IOException {
        var rows = values.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().feature() + "/" + e.getKey().player() + "/" + e.getKey().name()))
            .map(e -> Map.of("key", e.getKey(), "entry", e.getValue())).toList();
        JsonNode payload = FeatureParser.JSON.valueToTree(rows);
        return FeatureParser.JSON.writeValueAsBytes(Map.of("formatVersion", 1, "rows", payload, "checksum", FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(payload))));
    }
    private Map<Key, Entry> read() throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe variable file");
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = in.readNBytes(MAX_FILE_BYTES + 1); }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Variable storage size limit");
        JsonNode envelope;
        try (var parser = FeatureParser.JSON.createParser(bytes)) { envelope = FeatureParser.JSON.readTree(parser); if (parser.nextToken() != null) throw new IOException("Trailing variable data"); }
        if (envelope == null || !envelope.isObject() || envelope.size() != 3 || !envelope.path("formatVersion").isInt() || envelope.path("formatVersion").intValue() != 1
            || !envelope.path("rows").isArray() || envelope.path("rows").size() > MAX_KEYS || !envelope.path("checksum").isTextual()
            || !FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(envelope.get("rows"))).equals(envelope.path("checksum").textValue())) throw new IOException("Invalid variable envelope/checksum");
        Map<Key, Entry> loaded = new HashMap<>();
        for (JsonNode row : envelope.get("rows")) {
            if (!row.isObject() || row.size() != 2 || !row.has("key") || !row.has("entry")) throw new IOException("Invalid variable row");
            JsonNode k = row.get("key"), e = row.get("entry");
            if (!k.isObject() || k.size() != 3 || !k.path("feature").isTextual() || !k.path("name").isTextual()
                || !(k.path("player").isNull() || k.path("player").isTextual())
                || !e.isObject() || e.size() != 2 || !e.path("revision").isIntegralNumber() || !e.path("revision").canConvertToLong()) throw new IOException("Invalid variable key/revision");
            JsonNode v = e.path("value");
            if (!v.isObject() || v.size() != 4 || !v.path("type").isTextual() || !v.has("text") || !v.has("integer") || !v.has("bool")) throw new IOException("Invalid typed value");
            Type type = Type.valueOf(v.path("type").textValue());
            if (type == Type.TEXT ? !v.path("text").isTextual() || !v.path("integer").isNull() || !v.path("bool").isNull()
                : type == Type.INTEGER ? !v.path("integer").isIntegralNumber() || !v.path("integer").canConvertToLong() || !v.path("text").isNull() || !v.path("bool").isNull()
                : !v.path("bool").isBoolean() || !v.path("text").isNull() || !v.path("integer").isNull()) throw new IOException("Invalid value type");
            UUID player = k.path("player").isNull() ? null : UUID.fromString(k.path("player").textValue());
            if (player != null && !player.toString().equals(k.path("player").textValue())) throw new IOException("Noncanonical player UUID");
            Key key = new Key(k.path("feature").textValue(), player, k.path("name").textValue());
            Entry entry = new Entry(e.path("revision").longValue(), FeatureParser.JSON.treeToValue(v, Value.class));
            if (loaded.put(key, entry) != null) throw new IOException("Duplicate variable key");
        }
        return Map.copyOf(loaded);
    }
    private void write(byte[] bytes, Map<Key, Entry> committed) throws IOException {
        safeDirectory();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe variable target");
        Path temporary = root.resolve(".variables-pending-" + UUID.randomUUID());
        boolean replaced = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            faults.at("BEFORE_REPLACE");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
            state = committed; // Replacement is the commit point, even if directory flushing fails.
            try {
                faults.at("AFTER_REPLACE");
                try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
            } catch (IOException | UnsupportedOperationException | SecurityException e) { directoryFlushSupported = false; }
        } finally { if (!replaced) Files.deleteIfExists(temporary); }
    }
    private void safeDirectory() throws IOException {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))) throw new IOException("Unsafe variable directory");
        Files.createDirectories(root);
        if (!root.toRealPath().equals(root)) throw new IOException("Variable storage traverses a link");
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("Variable storage closed"); }
    @Override public synchronized void close() throws IOException { if (committing) throw new IllegalStateException("Variable commit in progress"); if (!closed) { closed = true; try { lock.release(); } finally { lockChannel.close(); } } }
}
