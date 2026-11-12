package dev.aetherscript.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aetherscript.core.FeatureParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owner-only credential persistence with safe creation permissions and atomic replacement. */
final class ProfileStore {
    private static final int MAX_BYTES = 4096;
    static PairingProfile load(Path requested) throws IOException {
        Path path = safePath(requested); validateParent(path.getParent()); validatePrivateFile(path);
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Invalid pairing profile");
            try (var parser = FeatureParser.JSON.createParser(bytes)) {
                JsonNode node = FeatureParser.JSON.readTree(parser);
                if (parser.nextToken() != null || node == null || !node.isObject() || (node.size() != 5 && node.size() != 8)
                    || !node.path("formatVersion").isInt() || (node.path("formatVersion").intValue() != 1 && node.path("formatVersion").intValue() != 2)
                    || !node.path("endpoint").isTextual() || !node.path("token").isTextual()
                    || !node.path("scope").asText().equals("status") || !node.path("expiresAt").isTextual()) throw new IOException("Invalid pairing profile");
                if (node.path("formatVersion").intValue() == 2) {
                    if (!node.path("sessionId").isTextual() || !node.path("serverId").isTextual() || !node.path("scopes").isArray()) throw new IOException("Invalid relay profile");
                    java.util.List<String> scopes = new java.util.ArrayList<>();
                    for (JsonNode scope : node.path("scopes")) { if (!scope.isTextual()) throw new IOException("Invalid scope"); scopes.add(scope.textValue()); }
                    return new PairingProfile(URI.create(node.path("endpoint").textValue()), node.path("token").textValue(), Instant.parse(node.path("expiresAt").textValue()), node.path("sessionId").textValue(), node.path("serverId").textValue(), scopes);
                }
                return new PairingProfile(URI.create(node.path("endpoint").textValue()), node.path("token").textValue(), Instant.parse(node.path("expiresAt").textValue()));
            } catch (Exception error) { throw new IOException("Invalid pairing profile. Pair again with the owner CLI."); }
        }
    }
    static void save(Path requested, PairingProfile profile) throws IOException {
        Path path = safePath(requested); createPrivateParents(path.getParent()); validateParent(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) validatePrivateFile(path);
        byte[] bytes = FeatureParser.JSON.writeValueAsBytes(profile.sessionId != null ? Map.of("formatVersion", 2, "endpoint", profile.endpoint().toString(), "token", profile.token(), "scope", "status", "expiresAt", profile.expiresAt().toString(), "sessionId", profile.sessionId, "serverId", profile.serverId, "scopes", profile.scopes) : Map.of("formatVersion", 1, "endpoint", profile.endpoint().toString(),
            "token", profile.token(), "scope", "status", "expiresAt", profile.expiresAt().toString()));
        Path pending = path.getParent().resolve(".aether-pairing-" + UUID.randomUUID());
        try {
            Files.createFile(pending, privateAttributes(path.getParent(), false));
            validatePrivateFile(pending);
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer content = ByteBuffer.wrap(bytes);
                while (content.hasRemaining()) channel.write(content);
                channel.force(true);
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
    static void forget(Path requested) throws IOException {
        Path path = safePath(requested);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        validateParent(path.getParent()); validatePrivateFile(path); Files.delete(path);
    }
    private static Path safePath(Path requested) throws IOException {
        Path path = requested.toAbsolutePath().normalize();
        if (path.getParent() == null || path.getRoot().toString().startsWith("\\\\")) throw new IOException("Use a local credential profile path");
        return path;
    }
    private static void createPrivateParents(Path parent) throws IOException {
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> missing = new ArrayList<>(); Path cursor = parent;
        while (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) { missing.addFirst(cursor); cursor = cursor.getParent(); }
        validateParent(cursor);
        for (Path directory : missing) Files.createDirectory(directory, privateAttributes(directory.getParent(), true));
    }
    private static void validateParent(Path parent) throws IOException {
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent) || !parent.toRealPath().equals(parent))
            throw new IOException("Pairing profile parent must be a real local directory");
    }
    private static UserPrincipal currentUser(Path path) throws IOException {
        return path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
    }
    private static FileAttribute<?> privateAttributes(Path parent, boolean directory) throws IOException {
        if (Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class))
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        if (Files.getFileStore(parent).supportsFileAttributeView(AclFileAttributeView.class)) {
            AclEntry.Builder builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(currentUser(parent))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
            List<AclEntry> entries = List.of(builder.build());
            return new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return entries; }
            };
        }
        throw new IOException("This filesystem cannot enforce owner-only credential permissions");
    }
    private static void validatePrivateFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || !Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(currentUser(path)))
            throw new IOException("Pairing profile must be a regular file owned by the current user");
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
            if (permissions.stream().anyMatch(permission -> !Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE).contains(permission)))
                throw new IOException("Pairing profile must permit only owner read/write (0600)");
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        UserPrincipal user = currentUser(path);
        if (acl == null || acl.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW && !entry.principal().equals(user)))
            throw new IOException("Pairing profile must have an owner-only ACL");
    }
}
