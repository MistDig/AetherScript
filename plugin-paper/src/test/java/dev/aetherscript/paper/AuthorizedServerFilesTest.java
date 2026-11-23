package dev.aetherscript.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizedServerFilesTest {
    @TempDir Path root;

    private AuthorizedServerFiles registry() throws Exception {
        return new AuthorizedServerFiles(root, root.resolve("AetherScript/file-management"));
    }

    @Test void previewChecksHashScopeAndRevocationAndRollbackSurvivesRestart() throws Exception {
        Path directory = Files.createDirectory(root.resolve("Other"));
        Path config = directory.resolve("settings.json");
        String original = "{\"motd\":\"old\",\"apiToken\":\"do not reveal\",\"nested\":{\"test\":true}}";
        Files.writeString(config, original);
        var files = registry();
        assertThrows(IllegalArgumentException.class, () -> files.defineConfig("escape", "../Other/settings.json", AuthorizedServerFiles.Format.JSON, Set.of("motd")));
        assertThrows(IllegalArgumentException.class, () -> files.defineConfig("engine", "AetherScript/config.yml", AuthorizedServerFiles.Format.YAML, Set.of("main")));
        assertThrows(IllegalArgumentException.class, () -> files.defineConfig("private", "Other/settings.json", AuthorizedServerFiles.Format.JSON, Set.of("apiToken")));
        files.defineConfig("welcome", "Other/settings.json", AuthorizedServerFiles.Format.JSON, Set.of("motd"));
        assertEquals(Map.of("motd", "old"), files.read("welcome").values());
        String hash = files.read("welcome").hash();
        assertThrows(IllegalArgumentException.class, () -> files.previewConfig("app", "welcome", "wrong", Map.of("motd", "new")));
        assertThrows(IllegalArgumentException.class, () -> files.previewConfig("app", "welcome", hash, Map.of("apiToken", "new")));
        var blocked = files.previewConfig("app", "welcome", hash, Map.of("motd", "new"));
        assertEquals(original, Files.readString(config));
        assertThrows(IllegalArgumentException.class, () -> files.approve(blocked.token(), "other-app", id -> true));
        assertThrows(IllegalArgumentException.class, () -> files.approve(blocked.token(), "app", id -> false));
        assertEquals(original, Files.readString(config));
        var pending = files.previewConfig("app", "welcome", hash, Map.of("motd", "new"));
        var result = files.approve(pending.token(), "app", id -> true);
        assertFalse(result.restartRequired());
        assertEquals(Map.of("motd", "new"), files.read("welcome").values());
        assertTrue(Files.readString(config).contains("do not reveal"));
        var restarted = registry();
        assertEquals(Map.of("motd", "new"), restarted.read("welcome").values());
        restarted.rollback(result.rollbackToken());
        assertEquals(original, Files.readString(config));
    }

    @Test void reviewedJarIsExactInboxArtifactAndApprovalRechecksDestination() throws Exception {
        var files = registry();
        assertThrows(IllegalArgumentException.class, () -> files.defineJar("self", "aether-new.jar", "AetherScript"));
        files.defineJar("demo", "Demo.jar", "Demo");
        Path inbox = root.resolve("AetherScript/file-management/inbox/reviewed.jar");
        byte[] artifact = plugin("Demo");
        Files.write(inbox, artifact);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        assertThrows(IllegalArgumentException.class, () -> files.stageJarInbox("app", "demo", "../reviewed.jar", digest));
        assertThrows(IllegalArgumentException.class, () -> files.stageJarInbox("app", "demo", "reviewed.jar", "0".repeat(64)));
        var plan = files.stageJarInbox("app", "demo", "reviewed.jar", digest);
        assertFalse(Files.exists(root.resolve("Demo.jar")));
        assertEquals("Demo", plan.details().get("name"));
        assertEquals(List.of("Vault"), plan.details().get("dependencies"));
        Files.write(inbox, plugin("Altered"));
        var applied = files.approve(plan.token(), "app", id -> true);
        assertTrue(applied.restartRequired());
        assertArrayEquals(artifact, Files.readAllBytes(root.resolve("Demo.jar")));
        registry().rollback(applied.rollbackToken());
        assertFalse(Files.exists(root.resolve("Demo.jar")));
    }

    private static byte[] plugin(String name) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: " + name + "\nversion: 1.0\nmain: demo.Plugin\ndepend: [Vault]\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("demo/Plugin.class"));
            zip.write(0);
        }
        return bytes.toByteArray();
    }
}
