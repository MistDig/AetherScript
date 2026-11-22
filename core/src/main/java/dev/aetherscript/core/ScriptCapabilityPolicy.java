package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Owner-managed execution ceilings. Construct and grant on the I/O worker; reads never touch disk. */
public final class ScriptCapabilityPolicy {
    public static final String MESSAGE = "runtime.message.send";
    public static final int MAX_BYTES = 65536, MAX_FEATURES = 128, MAX_CAPABILITIES = 32;
    private final Path file;
    private final java.util.function.Supplier<Set<String>> known;
    private volatile Map<String, Set<String>> grants;

    public ScriptCapabilityPolicy(Path file, Set<String> knownCapabilities) throws IOException {
        this(file, fixed(knownCapabilities));
    }
    private static java.util.function.Supplier<Set<String>> fixed(Set<String> capabilities) {
        Set<String> snapshot = Set.copyOf(capabilities);
        return () -> snapshot;
    }
    public ScriptCapabilityPolicy(Path file, java.util.function.Supplier<Set<String>> knownCapabilities) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        known = Objects.requireNonNull(knownCapabilities);
        if (!known.get().contains(MESSAGE)) throw new IllegalArgumentException("Message capability must be known");
        safeParent();
        grants = Files.exists(this.file, LinkOption.NOFOLLOW_LINKS) ? read() : Map.of();
    }
    private ScriptCapabilityPolicy() { file = null; known = () -> Set.of(MESSAGE); grants = Map.of(); }
    /** Corrupt storage can use this immutable ceiling while the owner repairs it. */
    public static ScriptCapabilityPolicy messageOnly() { return new ScriptCapabilityPolicy(); }
    public Set<String> allowed(String id) { FeatureParser.requireId(id); return grants.getOrDefault(id, Set.of(MESSAGE)); }
    public boolean configured(String id) { FeatureParser.requireId(id); return grants.containsKey(id); }
    /** A fresh browser grant authorizes new features; explicit local ceilings still take priority. */
    public void validateConnection(CompiledFeature feature, Set<String> capabilities) {
        if (!known.get().containsAll(capabilities) || !capabilities.containsAll(feature.definition().requires()))
            throw new ValidationException("$.requires", "CONNECTION_CAPABILITY_DENIED: reconnect and approve the required supported script capabilities");
        if (file == null || configured(feature.definition().id())) validate(feature);
    }
    /** Persist the owner's connection approval so an activated script survives app disconnect/restart. */
    public synchronized boolean approveConnection(CompiledFeature feature, Set<String> capabilities) throws IOException {
        validateConnection(feature, capabilities);
        if (configured(feature.definition().id()) || feature.definition().requires().stream().allMatch(MESSAGE::equals)) return false;
        grant(feature.definition().id(), capabilities); return true;
    }
    /** Compensation for a source activation that did not commit; never removes an existing owner ceiling. */
    public synchronized void removeGrant(String id) throws IOException {
        FeatureParser.requireId(id);
        if (!grants.containsKey(id)) return;
        Map<String, Set<String>> next = new TreeMap<>(grants); next.remove(id); persistGrants(next);
    }
    public void validate(CompiledFeature feature) {
        String id = feature.definition().id();
        Set<String> ceiling = allowed(id);
        for (String capability : feature.definition().requires()) {
            if (!known.get().contains(capability) || !ceiling.contains(capability))
                throw new ValidationException("$.requires", "Capability " + capability + " is not owner-approved for " + id + "; edit scripts/" + id + ".aether or ask the owner to update its capability ceiling");
        }
    }
    public synchronized void grant(String id, Set<String> capabilities) throws IOException {
        if (file == null) throw new IOException("Capability policy is read-only until owner storage is repaired");
        FeatureParser.requireId(id); Set<String> approved = checked(capabilities);
        Map<String, Set<String>> next = new TreeMap<>(grants); next.put(id, approved);
        persistGrants(next);
    }
    private void persistGrants(Map<String, Set<String>> next) throws IOException {
        if (file == null) throw new IOException("Capability policy is read-only until owner storage is repaired");
        if (next.size() > MAX_FEATURES) throw new IllegalArgumentException("Maximum 128 feature capability grants");
        Map<String, Set<String>> immutable = Collections.unmodifiableMap(next);
        byte[] payload = FeatureParser.JSON.writeValueAsBytes(canonical(immutable));
        byte[] bytes = FeatureParser.JSON.writeValueAsBytes(Map.of("formatVersion", 1, "grants", FeatureParser.JSON.readTree(payload), "checksum", FeatureParser.hash(payload)));
        if (bytes.length > MAX_BYTES) throw new IOException("Capability policy exceeds 64 KiB");
        safeParent(); safeFile();
        Path temporary = file.resolveSibling(".policy-pending-" + UUID.randomUUID());
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            safeParent(); safeFile();
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            grants = immutable;
            try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) { directory.force(true); }
            catch (IOException | UnsupportedOperationException ignored) { /* Windows may not support directory fsync. */ }
        } finally { Files.deleteIfExists(temporary); }
    }
    private Map<String, Set<String>> read() throws IOException {
        try {
            safeFile(); byte[] bytes;
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("Policy size limit");
            JsonNode envelope;
            try (var parser = FeatureParser.JSON.createParser(bytes)) {
                envelope = FeatureParser.JSON.readTree(parser);
                if (parser.nextToken() != null) throw new IOException("Trailing policy data");
            }
            if (envelope == null || !envelope.isObject() || envelope.size() != 3 || !envelope.path("formatVersion").isInt() || envelope.path("formatVersion").intValue() != 1
                || !envelope.path("grants").isObject() || !envelope.path("checksum").isTextual()) throw new IOException("Invalid policy envelope");
            JsonNode entries = envelope.get("grants");
            if (entries.size() > MAX_FEATURES || !FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(entries)).equals(envelope.path("checksum").textValue())) throw new IOException("Policy bounds or checksum mismatch");
            Map<String, Set<String>> loaded = new TreeMap<>();
            for (var entry : entries.properties()) {
                FeatureParser.requireId(entry.getKey()); JsonNode values = entry.getValue();
                if (!values.isArray() || values.size() > MAX_CAPABILITIES) throw new IOException("Invalid capability array");
                Set<String> caps = new HashSet<>();
                for (JsonNode value : values) if (!value.isTextual() || !caps.add(value.textValue())) throw new IOException("Invalid or duplicate capability");
                var installed = known.get();
                if (caps.stream().anyMatch(cap -> !installed.contains(cap) && !cap.matches("addon\\.[a-z][a-z0-9_]{0,23}\\.[a-z][a-z0-9_]{0,31}")))
                    throw new IOException("Unknown stored capability");
                loaded.put(entry.getKey(), Set.copyOf(caps));
            }
            return Collections.unmodifiableMap(loaded);
        } catch (Exception failure) { throw new IOException("CAPABILITY_POLICY_CORRUPT: repair owner capability policy " + file.getFileName(), failure); }
    }
    private Set<String> checked(Set<String> capabilities) {
        if (capabilities.size() > MAX_CAPABILITIES || !known.get().containsAll(capabilities)) throw new IllegalArgumentException("Unknown capability or more than 32 capabilities");
        return Set.copyOf(capabilities);
    }
    private static Map<String, List<String>> canonical(Map<String, Set<String>> values) {
        Map<String, List<String>> result = new TreeMap<>(); values.forEach((id, caps) -> result.put(id, caps.stream().sorted().toList())); return result;
    }
    private void safeFile() throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe capability policy file");
    }
    private void safeParent() throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Capability policy requires a parent directory");
        // Check every existing ancestor before creating a directory through it.
        for (Path cursor = parent; cursor != null; cursor = cursor.getParent())
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor))) throw new IOException("Unsafe capability policy parent");
        Files.createDirectories(parent);
        if (!parent.toRealPath().equals(parent)) throw new IOException("Capability policy parent traverses a link");
    }
}
