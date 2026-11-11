package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TextIntegrationTest {
    @TempDir Path temp;

    String example() throws Exception {
        return Files.readString(Path.of("../examples/hello-ai.aether"), StandardCharsets.UTF_8);
    }

    SourceFile source(String text) {
        return new SourceFile("hello-ai.aether", "aether", text.getBytes(StandardCharsets.UTF_8));
    }

    void apply(FeatureManager.Change change, RevisionStore store, Registry registry) throws Exception {
        registry.replace(registry.snapshot().generation(), change.target());
        store.commit(change.durable());
        store.prune();
    }

    @Test void normalParserSharesTypedIrAndRetainsOriginalSourceHash() throws Exception {
        byte[] bytes = example().getBytes(StandardCharsets.UTF_8);
        var text = new FeatureParser().parse(bytes, "aether");
        var yaml = new FeatureParser().parse(EngineTest.YAML.getBytes(StandardCharsets.UTF_8), "yaml");
        assertEquals(yaml.definition(), text.definition());
        assertEquals(yaml.irHash(), text.irHash());
        assertEquals(FeatureParser.hash(bytes), text.sourceHash());
        assertNotEquals(yaml.sourceHash(), text.sourceHash());
    }

    @Test void textualSourceUsesNormalReloadPermissionAndRetirementPaths() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Files.writeString(scripts.resolve("hello-ai.aether"), example());
        var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store);
        var registry = new Registry();
        apply(manager.startup(), store, registry);
        List<String> messages = new ArrayList<>();
        assertFalse(registry.run("hello-ai", true, permission -> false, messages::add));
        assertTrue(messages.isEmpty());
        assertTrue(registry.run("hello-ai", true, permission -> permission.equals("aetherscript.example.hello"), messages::add));
        assertEquals(List.of("Hello from AetherScript!"), messages);
        Files.delete(scripts.resolve("hello-ai.aether"));
        apply(manager.reload(null, registry.snapshot()), store, registry);
        assertFalse(registry.run("hello-ai", true, permission -> true, messages::add));
    }

    @Test void invalidTextEditPreservesWorkingRevisionAcrossRestart() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Path file = scripts.resolve("hello-ai.aether");
        Files.writeString(file, example());
        var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store);
        var registry = new Registry();
        apply(manager.startup(), store, registry);
        var before = registry.snapshot();
        Files.writeString(file, example().replace("let count = 2 + 3", "let count = missing"));
        var rejected = assertThrows(FeatureManager.Rejected.class, () -> manager.reload("hello-ai", before));
        assertTrue(rejected.diagnostics().stream().anyMatch(message -> message.contains("hello-ai.aether")));
        assertTrue(rejected.diagnostics().stream().anyMatch(message -> message.contains("line 4")));
        assertSame(before, registry.snapshot());
        var reopened = new RevisionStore(temp.resolve("storage"), 5);
        var startup = new FeatureManager(new ScriptStore(scripts), reopened).startup();
        assertEquals(before.features(), startup.target());
        assertEquals("aether", reopened.recover("hello-ai").revision().format());
        assertArrayEquals(example().getBytes(StandardCharsets.UTF_8), reopened.recover("hello-ai").source().bytes());
    }

    @Test void textualRollbackRetainsEditableFileAndHoldsAcrossRestart() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Path file = scripts.resolve("hello-ai.aether");
        Files.writeString(file, example());
        var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store);
        var registry = new Registry();
        apply(manager.startup(), store, registry);
        String changed = example().replace("send greeting + \" from AetherScript!\"", "send \"Revision B\"");
        Files.writeString(file, changed);
        apply(manager.reload("hello-ai", registry.snapshot()), store, registry);
        apply(manager.rollback("hello-ai", registry.snapshot()), store, registry);
        assertEquals(changed, Files.readString(file));
        var reopened = new RevisionStore(temp.resolve("storage"), 5);
        var restored = new FeatureManager(new ScriptStore(scripts), reopened).startup();
        assertEquals("Hello from AetherScript!", restored.target().get("hello-ai").definition().command().actions().getFirst().text());
        assertTrue(reopened.state().features().get("hello-ai").rollbackHeld());
    }

    @Test void mixedFormatsRejectDuplicateFeatureIdsBeforeReplacingRegistry() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Files.writeString(scripts.resolve("hello-ai.aether"), example());
        Files.writeString(scripts.resolve("hello-ai.yml"), EngineTest.YAML);
        var inspection = new ScriptStore(scripts).inspect("hello-ai").get("hello-ai");
        assertFalse(inspection.valid());
        assertTrue(inspection.error().reason().contains("Duplicate feature ID"));
        assertTrue(inspection.fileName().contains("hello-ai.aether"));
        assertTrue(inspection.fileName().contains("hello-ai.yml"));
    }

    @Test void structuredToTextMigrationAndRollbackKeepBothFormatsRecoverable() throws Exception {
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Path yaml = scripts.resolve("hello-ai.yml");
        Path text = scripts.resolve("hello-ai.aether");
        Files.writeString(yaml, EngineTest.YAML);
        var store = new RevisionStore(temp.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store);
        var registry = new Registry();
        apply(manager.startup(), store, registry);
        String yamlHash = registry.snapshot().features().get("hello-ai").sourceHash();
        Files.delete(yaml);
        Files.writeString(text, example());
        apply(manager.reload("hello-ai", registry.snapshot()), store, registry);
        assertEquals("aether", store.recover("hello-ai").revision().format());
        assertNotEquals(yamlHash, registry.snapshot().features().get("hello-ai").sourceHash());
        apply(manager.rollback("hello-ai", registry.snapshot()), store, registry);
        assertEquals("yml", store.recover("hello-ai").revision().format());
        assertEquals(yamlHash, registry.snapshot().features().get("hello-ai").sourceHash());
        assertEquals(example(), Files.readString(text));
        var reopened = new RevisionStore(temp.resolve("storage"), 5);
        var restoredManager = new FeatureManager(new ScriptStore(scripts), reopened);
        var restoredRegistry = new Registry();
        apply(restoredManager.startup(), reopened, restoredRegistry);
        assertEquals(yamlHash, restoredRegistry.snapshot().features().get("hello-ai").sourceHash());
        apply(restoredManager.reload("hello-ai", restoredRegistry.snapshot()), reopened, restoredRegistry);
        assertEquals("aether", reopened.recover("hello-ai").revision().format());
        assertEquals(3, reopened.state().features().get("hello-ai").active());
        assertFalse(reopened.state().features().get("hello-ai").rollbackHeld());
    }
}
