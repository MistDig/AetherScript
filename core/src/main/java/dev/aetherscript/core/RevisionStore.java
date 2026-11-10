package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Single-writer durable metadata; only an atomic manifest replacement makes staged content committed. */
public final class RevisionStore {
    public static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
    public record Revision(long number, String hash, String format, String savedAt) {}
    public record History(long nextNumber, long active, boolean rollbackHeld, List<Revision> revisions) {
        public History { revisions = List.copyOf(revisions); }
        public Revision activeRevision() { return revisions.stream().filter(r -> r.number() == active).findFirst().orElse(null); }
    }
    public record State(long sequence, Map<String, History> features) {
        public State { features = Collections.unmodifiableMap(new TreeMap<>(features)); }
    }
    public record Plan(State before, State after) { public boolean changed() { return !before.equals(after); } }
    public record Saved(Revision revision, SourceFile source, CompiledFeature compiled) {}
    @FunctionalInterface public interface Faults { void at(String boundary) throws IOException; }
    private final Path root, revisions, manifest;
    private final int retain;
    private final Faults faults;
    private volatile State state;
    private volatile boolean directoryFlushSupported = true;

    public RevisionStore(Path root, int retain) throws IOException { this(root, retain, boundary -> {}); }
    public RevisionStore(Path root, int retain, Faults faults) throws IOException {
        if (retain < 1 || retain > 50) throw new IllegalArgumentException("retainVersions must be 1..50");
        this.root = root.toAbsolutePath().normalize(); this.retain = retain; this.faults = faults;
        safeDirectory(this.root); revisions = this.root.resolve("revisions"); safeDirectory(revisions);
        manifest = this.root.resolve("manifest.json");
        state = Files.exists(manifest, LinkOption.NOFOLLOW_LINKS) ? readState() : new State(0, Map.of());
    }
    public State state() { return state; }
    public boolean directoryFlushSupported() { return directoryFlushSupported; }
    public int retainVersions() { return retain; }

    public Plan prepare(Map<String, SourceFile> updates, Set<String> deactivate) throws IOException { return prepare(updates, deactivate, Map.of()); }
    public Plan prepare(Map<String, SourceFile> updates, Set<String> deactivate, Map<String, Long> recovered) throws IOException {
        State before = state; Map<String, History> next = new TreeMap<>(before.features());
        for (var entry : new TreeMap<>(updates).entrySet()) {
            String id = entry.getKey(); FeatureParser.requireId(id); SourceFile source = entry.getValue();
            CompiledFeature feature = source.compile();
            if (!feature.definition().id().equals(id)) throw new ValidationException("$.id", "Source ID does not match revision ID");
            History old = next.getOrDefault(id, new History(1, 0, false, List.of()));
            Revision active = old.activeRevision();
            writeSource(id, source);
            if (active != null && active.hash().equals(source.hash()) && active.format().equals(source.format())) {
                next.put(id, new History(old.nextNumber(), old.active(), false, trim(old.revisions(), old.active())));
                continue;
            }
            Revision revision = new Revision(old.nextNumber(), source.hash(), source.format(), Instant.now().toString());
            List<Revision> history = new ArrayList<>(old.revisions()); history.add(revision);
            next.put(id, new History(Math.incrementExact(old.nextNumber()), revision.number(), false, trim(history, revision.number())));
        }
        recovered.forEach((id, number) -> {
            History history = next.get(id);
            if (history == null || history.revisions().stream().noneMatch(r -> r.number() == number)) throw new IllegalArgumentException("Unknown recovered revision");
            next.put(id, new History(history.nextNumber(), number, history.rollbackHeld(), trim(history.revisions(), number)));
        });
        for (String id : deactivate) {
            FeatureParser.requireId(id); History old = next.get(id);
            if (old != null) next.put(id, new History(old.nextNumber(), 0, false, trim(old.revisions(), 0)));
        }
        next.replaceAll((id, h) -> new History(h.nextNumber(), h.active(), h.rollbackHeld(), trim(h.revisions(), h.active())));
        // Keep bounded metadata even for retired features. Operators may archive internal storage manually offline.
        if (next.size() > 128) throw new ValidationException("$", "Maximum 128 tracked feature histories");
        if (next.equals(before.features())) return new Plan(before, before);
        return new Plan(before, new State(Math.incrementExact(before.sequence()), next));
    }
    public Plan rollback(String id) throws IOException {
        FeatureParser.requireId(id); State before = state; History history = before.features().get(id);
        if (history == null) throw new ValidationException("$.id", "No saved history for this feature");
        List<Revision> candidates = history.revisions().stream().filter(r -> history.active() == 0 || r.number() < history.active())
            .sorted(Comparator.comparingLong(Revision::number).reversed()).toList();
        for (Revision revision : candidates) {
            try {
                load(id, revision); // Revalidate against current schema and capability allowlist.
                Map<String, History> next = new TreeMap<>(before.features());
                next.put(id, new History(history.nextNumber(), revision.number(), true, trim(history.revisions(), revision.number())));
                return new Plan(before, new State(Math.incrementExact(before.sequence()), next));
            } catch (IOException | ValidationException ignored) { /* Skip corrupt/unsupported history, never execute it. */ }
        }
        throw new ValidationException("$.id", "No earlier readable, valid saved revision is available");
    }
    /** Recovery fallback checks active first, then earlier versions; never chooses an uncommitted source file. */
    public Saved recover(String id) throws IOException {
        FeatureParser.requireId(id); History history = state.features().get(id);
        if (history == null || history.active() == 0) return null;
        List<Revision> candidates = new ArrayList<>();
        if (history.activeRevision() != null) candidates.add(history.activeRevision());
        history.revisions().stream().filter(r -> r.number() < history.active()).sorted(Comparator.comparingLong(Revision::number).reversed()).forEach(candidates::add);
        for (Revision revision : candidates) {
            try { return load(id, revision); } catch (IOException | ValidationException ignored) { }
        }
        throw new IOException("STORAGE_CORRUPT: no readable saved revision for " + id);
    }
    public Saved load(String id, Revision revision) throws IOException {
        FeatureParser.requireId(id); validateRevision(revision);
        byte[] bytes = readBounded(sourcePath(id, revision.hash()), FeatureParser.MAX_BYTES);
        if (!FeatureParser.hash(bytes).equals(revision.hash())) throw new IOException("STORAGE_CORRUPT: source checksum mismatch for " + id);
        SourceFile source = new SourceFile(id + "." + revision.format(), revision.format(), bytes);
        CompiledFeature compiled = source.compile();
        if (!compiled.definition().id().equals(id)) throw new IOException("STORAGE_CORRUPT: source ID mismatch");
        return new Saved(revision, source, compiled);
    }
    public Plan selectRecovered(Map<String, Long> activeVersions, Set<String> deactivate) {
        State before = state; Map<String, History> next = new TreeMap<>(before.features());
        activeVersions.forEach((id, number) -> {
            History history = next.get(id);
            if (history == null || history.revisions().stream().noneMatch(r -> r.number() == number)) throw new IllegalArgumentException("Unknown saved revision");
            next.put(id, new History(history.nextNumber(), number, history.rollbackHeld(), trim(history.revisions(), number)));
        });
        deactivate.forEach(id -> { History h = next.get(id); if (h != null) next.put(id, new History(h.nextNumber(), 0, false, trim(h.revisions(), 0))); });
        return new Plan(before, next.equals(before.features()) ? before : new State(Math.incrementExact(before.sequence()), next));
    }
    public void commit(Plan plan) throws IOException {
        if (state != plan.before()) throw new IOException("STALE_PLAN: durable state changed");
        if (!plan.changed()) return;
        writeManifest(plan.after()); state = plan.after();
    }
    /** Compensating restore after an activation/audit failure. Caller must quarantine if this fails. */
    public void restore(State previous) throws IOException { writeManifest(previous); state = previous; }

    public void prune() throws IOException {
        safeDirectory(root); safeDirectory(revisions);
        for (var entry : state.features().entrySet()) {
            String id = entry.getKey(); Path directory = revisions.resolve(id);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            safeDirectory(directory);
            Set<String> keep = new HashSet<>(); entry.getValue().revisions().forEach(r -> keep.add(r.hash() + ".source"));
            try (var paths = Files.list(directory)) {
                for (Path file : paths.limit(4097).toList()) {
                    String name = file.getFileName().toString();
                    if (name.matches("[a-f0-9]{64}\\.source") && !keep.contains(name) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
                    else if (name.matches("\\.pending-[a-f0-9-]+") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
                }
            }
        }
    }
    private List<Revision> trim(List<Revision> revisions, long active) {
        List<Revision> result = new ArrayList<>(revisions); result.sort(Comparator.comparingLong(Revision::number));
        while (result.size() > retain) {
            int index = 0; while (result.get(index).number() == active) index++;
            result.remove(index);
        }
        return List.copyOf(result);
    }
    private Path sourcePath(String id, String hash) throws IOException {
        safeDirectory(root); safeDirectory(revisions);
        if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid content hash");
        Path dir = revisions.resolve(id); safeDirectory(dir); return dir.resolve(hash + ".source");
    }
    private void writeSource(String id, SourceFile source) throws IOException {
        Path path = sourcePath(id, source.hash());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (FeatureParser.hash(readBounded(path, FeatureParser.MAX_BYTES)).equals(source.hash())) return;
            // A validated editable source can repair the exact content-addressed bytes, never a different revision.
            atomicWrite(path, source.bytes(), false);
            return;
        }
        atomicWrite(path, source.bytes(), false);
    }
    private void writeManifest(State target) throws IOException {
        safeDirectory(root);
        JsonNode payload = FeatureParser.JSON.valueToTree(target);
        byte[] canonical = FeatureParser.JSON.writeValueAsBytes(payload);
        byte[] envelope = FeatureParser.JSON.writeValueAsBytes(Map.of("formatVersion", 1, "state", payload, "checksum", FeatureParser.hash(canonical)));
        if (envelope.length > MAX_MANIFEST_BYTES) throw new IOException("Manifest size limit");
        atomicWrite(manifest, envelope, true);
    }
    private void atomicWrite(Path target, byte[] bytes, boolean manifestWrite) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe storage target");
        Path temporary = target.getParent().resolve(".pending-" + UUID.randomUUID());
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            if (manifestWrite) { faults.at("AFTER_MANIFEST_FLUSH"); faults.at("BEFORE_MANIFEST_REPLACE"); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // Windows often cannot open directories for fsync. Do not claim power-loss durability there.
            try (FileChannel directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { directory.force(true); }
            catch (IOException | UnsupportedOperationException e) { directoryFlushSupported = false; }
        } finally { Files.deleteIfExists(temporary); }
    }
    private State readState() throws IOException {
        try {
            byte[] bytes = readBounded(manifest, MAX_MANIFEST_BYTES); JsonNode envelope;
            try (var parser = FeatureParser.JSON.createParser(bytes)) {
                envelope = FeatureParser.JSON.readTree(parser);
                if (parser.nextToken() != null) throw new IOException("Trailing manifest data");
            }
            if (envelope == null || !envelope.isObject() || envelope.size() != 3 || !envelope.path("formatVersion").isInt() || envelope.path("formatVersion").intValue() != 1
                || !envelope.path("state").isObject() || !envelope.path("checksum").isTextual()) throw new IOException("Invalid manifest envelope");
            if (!FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(envelope.get("state"))).equals(envelope.path("checksum").textValue())) throw new IOException("Manifest checksum mismatch");
            State loaded = FeatureParser.JSON.treeToValue(envelope.get("state"), State.class);
            if (loaded.sequence() < 0 || loaded.features().size() > 128) throw new IOException("Manifest bounds");
            for (var entry : loaded.features().entrySet()) {
                FeatureParser.requireId(entry.getKey()); History history = entry.getValue();
                if (history == null || history.nextNumber() < 1 || history.revisions().size() > 50 || history.active() < 0) throw new IOException("Invalid history");
                Set<Long> numbers = new HashSet<>();
                for (Revision revision : history.revisions()) {
                    validateRevision(revision);
                    if (!numbers.add(revision.number()) || revision.number() >= history.nextNumber()) throw new IOException("Invalid revision sequence");
                }
                if (history.active() != 0 && history.activeRevision() == null) throw new IOException("Missing active revision");
            }
            return loaded;
        } catch (Exception e) { throw new IOException("STORAGE_CORRUPT: storage/manifest.json failed validation; restore it from an owner backup before retrying", e); }
    }
    private static void validateRevision(Revision revision) throws IOException {
        if (revision == null || revision.number() < 1 || revision.hash() == null || !revision.hash().matches("[a-f0-9]{64}")
            || !Set.of("yaml", "yml", "json").contains(revision.format()) || revision.savedAt() == null) throw new IOException("Invalid saved revision");
        try { Instant.parse(revision.savedAt()); } catch (RuntimeException e) { throw new IOException("Invalid revision timestamp", e); }
    }
    private static byte[] readBounded(Path file, int max) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe or missing storage file");
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(max + 1); if (bytes.length > max) throw new IOException("Storage size limit"); return bytes;
        }
    }
    private static void safeDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) throw new IOException("Unsafe storage directory");
        Files.createDirectories(path);
        if (!path.toRealPath().equals(path.toAbsolutePath().normalize())) throw new IOException("Storage path escapes through a link");
    }
}
