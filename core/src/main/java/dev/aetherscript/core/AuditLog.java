package dev.aetherscript.core;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
public final class AuditLog {
    private final Path file;
    public AuditLog(Path file) { this.file = file; }
    public synchronized void append(String request, String principal, String operation, String outcome, long generation, Map<String, CompiledFeature> features) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        features.forEach((id, feature) -> hashes.put(id, feature.sourceHash()));
        byte[] bytes = (FeatureParser.JSON.writeValueAsString(Map.of("time", Instant.now().toString(), "request", request,
            "principal", principal, "operation", operation, "outcome", outcome, "generation", generation, "sourceHashes", hashes)) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
    }
}
