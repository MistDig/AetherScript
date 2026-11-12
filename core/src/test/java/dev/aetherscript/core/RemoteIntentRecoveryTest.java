package dev.aetherscript.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteIntentRecoveryTest {
    @TempDir Path root;
    String original() throws Exception { return Files.readString(Path.of("../examples/hello-ai.aether")); }
    void halt(String boundary) throws Exception {
        Files.createDirectories(root.resolve("scripts")); Files.writeString(root.resolve("scripts/hello-ai.aether"), original());
        Path next = root.resolve("next.aether"); Files.writeString(next, original().replace("\"Hello\"", "\"Committed\""));
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> type : List.of(RemoteIntentCrashFixture.class, RevisionStore.class, com.fasterxml.jackson.core.JsonFactory.class,
                com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.annotation.JsonCreator.class, org.yaml.snakeyaml.Yaml.class))
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(javaExecutable, "-cp", String.join(File.pathSeparator, entries), RemoteIntentCrashFixture.class.getName(), root.toString(), next.toString(), boundary)
            .redirectErrorStream(true).redirectOutput(root.resolve("child.log").toFile()).start();
        try {
            assertTrue(child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(73, child.exitValue(), Files.readString(root.resolve("child.log")));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
    @ParameterizedTest @ValueSource(strings = {"beforeCommit", "afterCommit", "afterPublish"})
    void haltedJvmPublishesOnlyDurablyCommittedRemoteSource(String boundary) throws Exception {
        halt(boundary);
        var manager = new FeatureManager(new ScriptStore(root.resolve("scripts")), new RevisionStore(root.resolve("storage"), 5));
        var startup = manager.startup();
        String expected = boundary.equals("beforeCommit") ? original() : Files.readString(root.resolve("next.aether"));
        assertEquals(expected, Files.readString(root.resolve("scripts/hello-ai.aether")));
        assertEquals(new SourceFile("hello-ai.aether", "aether", expected.getBytes(java.nio.charset.StandardCharsets.UTF_8)).compile(), startup.target().get("hello-ai"));
        try (var intents = Files.list(root.resolve("scripts/.remote-intents"))) { assertEquals(0, intents.count()); }
    }
    @Test void recoveryPreservesOwnerEditAfterCommittedRemoteActivation() throws Exception {
        halt("afterCommit"); String owner = original().replace("\"Hello\"", "\"Owner\"");
        Files.writeString(root.resolve("scripts/hello-ai.aether"), owner);
        var manager = new FeatureManager(new ScriptStore(root.resolve("scripts")), new RevisionStore(root.resolve("storage"), 5));
        var startup = manager.startup();
        assertEquals(owner, Files.readString(root.resolve("scripts/hello-ai.aether")));
        assertEquals("Owner from AetherScript!", startup.target().get("hello-ai").definition().command().actions().getFirst().text());
    }
    @Test void unchangedPriorActiveHashCannotRecoverAnUncommittedIntent() throws Exception {
        halt("sameHashBeforeCommit");
        String owner = original().replace("\"Hello\"", "\"Owner pending\"");
        var manager = new FeatureManager(new ScriptStore(root.resolve("scripts")), new RevisionStore(root.resolve("storage"), 5));
        manager.startup(); assertEquals(owner, Files.readString(root.resolve("scripts/hello-ai.aether")));
    }
}
