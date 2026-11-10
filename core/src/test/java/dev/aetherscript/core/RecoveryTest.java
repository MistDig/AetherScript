package dev.aetherscript.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    @TempDir Path temp;
    SourceFile source(String text) {
        return new SourceFile("hello-ai.yml", "yml", EngineTest.YAML.replace("Hello from AetherScript!", text).getBytes(StandardCharsets.UTF_8));
    }
    RevisionStore store(int retain) throws IOException { return new RevisionStore(temp.resolve("storage"), retain); }
    void save(RevisionStore store, String text) throws IOException { store.commit(store.prepare(Map.of("hello-ai", source(text)), Set.of())); store.prune(); }
    Path sourcePath(RevisionStore.Revision revision) { return temp.resolve("storage/revisions/hello-ai/" + revision.hash() + ".source"); }
    FeatureManager manager(RevisionStore store) throws IOException {
        Files.createDirectories(temp.resolve("scripts"));
        return new FeatureManager(new ScriptStore(temp.resolve("scripts")), store);
    }
    void edit(String source) throws IOException { Files.createDirectories(temp.resolve("scripts")); Files.writeString(temp.resolve("scripts/hello-ai.yml"), source); }
    void apply(FeatureManager.Change change, RevisionStore store, Registry registry) throws IOException {
        registry.replace(registry.snapshot().generation(), change.target()); store.commit(change.durable()); store.prune();
    }
    @Test void committedSourceSurvivesRestartAndRetainsExactBytes() throws Exception {
        var store = store(5); save(store, "A");
        var reopened = store(5); var saved = reopened.recover("hello-ai");
        assertEquals(1, saved.revision().number()); assertArrayEquals(source("A").bytes(), saved.source().bytes());
        assertEquals("A", saved.compiled().definition().command().actions().getFirst().text());
    }
    @Test void unchangedAndInvalidEditsNeverConsumeHistory() throws Exception {
        var store = store(5); save(store, "A"); var before = store.state();
        assertFalse(store.prepare(Map.of("hello-ai", source("A")), Set.of()).changed());
        var invalid = new SourceFile("hello-ai.yml", "yml", EngineTest.YAML.replace("target: sender", "target: everyone").getBytes());
        assertThrows(ValidationException.class, () -> store.prepare(Map.of("hello-ai", invalid), Set.of()));
        assertSame(before, store.state()); assertEquals(1, store.state().features().get("hello-ai").revisions().size());
    }
    @Test void fiveVersionRetentionRemovesOnlyOldUnreferencedContent() throws Exception {
        var store = store(5); for (int i = 1; i <= 8; i++) save(store, "Version " + i);
        var history = store.state().features().get("hello-ai");
        assertEquals(List.of(4L,5L,6L,7L,8L), history.revisions().stream().map(RevisionStore.Revision::number).toList());
        assertEquals(8, history.active()); assertEquals(9, history.nextNumber());
        try (var files = Files.list(temp.resolve("storage/revisions/hello-ai"))) { assertEquals(5, files.count()); }
    }
    @Test void reducingRetentionProtectsAnOlderActiveRollback() throws Exception {
        var store = store(5); for (int i = 1; i <= 5; i++) save(store, "V" + i);
        store.commit(store.rollback("hello-ai")); store.commit(store.rollback("hello-ai")); store.commit(store.rollback("hello-ai")); store.commit(store.rollback("hello-ai"));
        var reopened = store(2); reopened.commit(reopened.prepare(Map.of(), Set.of())); reopened.prune();
        var history = reopened.state().features().get("hello-ai");
        assertEquals(1, history.active()); assertEquals(2, history.revisions().size());
        assertEquals(List.of(1L,5L), history.revisions().stream().map(RevisionStore.Revision::number).toList());
        assertEquals("V1", reopened.recover("hello-ai").compiled().definition().command().actions().getFirst().text());
    }
    @Test void rollbackReusesSavedRevisionAndNeverOverwritesEditableFile() throws Exception {
        var store = store(5); save(store, "A"); save(store, "B"); edit(source("B").text());
        Registry registry = new Registry(); registry.replace(0, Map.of("hello-ai", source("B").compile()));
        var manager = manager(store); apply(manager.rollback("hello-ai", registry.snapshot()), store, registry);
        assertEquals(1, store.state().features().get("hello-ai").active()); assertEquals(2, store.state().features().get("hello-ai").revisions().size());
        assertEquals(source("B").text(), Files.readString(temp.resolve("scripts/hello-ai.yml")));
        var reopened = store(5); var startup = manager(reopened).startup();
        assertEquals(source("A").compile(), startup.target().get("hello-ai"));
        assertTrue(reopened.state().features().get("hello-ai").rollbackHeld());
        var reload = manager(reopened).reload("hello-ai", registry.snapshot()); apply(reload, reopened, registry);
        assertEquals("B", registry.snapshot().features().get("hello-ai").definition().command().actions().getFirst().text());
        assertFalse(reopened.state().features().get("hello-ai").rollbackHeld());
    }
    @Test void invalidEditableSourceUsesSavedRevisionAtStartup() throws Exception {
        var store = store(5); save(store, "A"); edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        var manager = manager(store); var startup = manager.startup();
        assertEquals(source("A").compile(), startup.target().get("hello-ai"));
        assertTrue(startup.messages().stream().anyMatch(s -> s.contains("line 11")));
        assertTrue(startup.messages().stream().anyMatch(s -> s.contains("Allowed: sender")));
        assertFalse(startup.durable().changed());
    }
    @Test void validNewEditableSourceLoadsAtStartup() throws Exception {
        var store = store(5); save(store, "A"); edit(source("B").text());
        var startup = manager(store).startup(); store.commit(startup.durable());
        assertEquals(source("B").compile(), startup.target().get("hello-ai")); assertEquals(2, store.state().features().get("hello-ai").active());
    }
    @Test void invalidNewScriptDoesNotStopUnrelatedGoodScript() throws Exception {
        var store = store(5); edit(source("A").text());
        Files.writeString(temp.resolve("scripts/bad.yml"), "id: bad\nsyntax: [");
        var startup = manager(store).startup();
        assertEquals(Set.of("hello-ai"), startup.target().keySet()); assertTrue(startup.messages().stream().anyMatch(s -> s.contains("bad.yml")));
    }
    @Test void corruptActiveContentFallsBackToEarlierVerifiedRevision() throws Exception {
        var store = store(5); save(store, "A"); save(store, "B");
        Files.writeString(sourcePath(store.state().features().get("hello-ai").activeRevision()), "damaged");
        edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        var startup = manager(store).startup(); store.commit(startup.durable());
        assertEquals(source("A").compile(), startup.target().get("hello-ai")); assertEquals(1, store.state().features().get("hello-ai").active());
    }
    @Test void validEditableFileRepairsItsExactCorruptSavedBytes() throws Exception {
        var store = store(5); save(store, "A"); var revision = store.state().features().get("hello-ai").activeRevision();
        Files.writeString(sourcePath(revision), "damaged"); edit(source("A").text());
        var startup = manager(store).startup(); store.commit(startup.durable());
        assertArrayEquals(source("A").bytes(), Files.readAllBytes(sourcePath(revision)));
        assertEquals(1, store.state().features().get("hello-ai").revisions().size());
    }
    @Test void corruptOnlyBackupIsDisabledWhileOtherSourcesLoad() throws Exception {
        var store = store(5); save(store, "A");
        Files.writeString(sourcePath(store.state().features().get("hello-ai").activeRevision()), "damaged");
        edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        Files.writeString(temp.resolve("scripts/other.yml"), EngineTest.YAML.replace("id: hello-ai", "id: other").replace("name: helloai", "name: other"));
        var startup = manager(store).startup(); store.commit(startup.durable());
        assertEquals(Set.of("other"), startup.target().keySet()); assertEquals(0, store.state().features().get("hello-ai").active());
    }
    @Test void corruptManifestFailsClosedWithoutGuessingFromOrphanFiles() throws Exception {
        var store = store(5); save(store, "A"); Files.writeString(temp.resolve("storage/manifest.json"), "{\"state\":{}}");
        IOException error = assertThrows(IOException.class, () -> store(5)); assertTrue(error.getMessage().contains("STORAGE_CORRUPT"));
    }
    @ParameterizedTest @ValueSource(strings = {"AFTER_MANIFEST_FLUSH", "BEFORE_MANIFEST_REPLACE"})
    void interruptedManifestSaveKeepsPreviousCommittedRevision(String boundary) throws Exception {
        var initial = store(5); save(initial, "A");
        var failing = new RevisionStore(temp.resolve("storage"), 5, b -> { if (b.equals(boundary)) throw new IOException("injected interruption"); });
        var plan = failing.prepare(Map.of("hello-ai", source("B")), Set.of());
        assertThrows(IOException.class, () -> failing.commit(plan));
        assertEquals(source("A").compile(), store(5).recover("hello-ai").compiled());
        assertEquals(1, store(5).state().features().get("hello-ai").revisions().size());
    }
    @Test void stagedButUncommittedContentAndPendingManifestAreIgnoredAfterCrash() throws Exception {
        var store = store(5); save(store, "A"); store.prepare(Map.of("hello-ai", source("B")), Set.of());
        Files.writeString(temp.resolve("storage/.pending-crashed"), "half-written data");
        assertEquals(source("A").compile(), store(5).recover("hello-ai").compiled());
    }
    @ParameterizedTest @ValueSource(strings = {"AFTER_MANIFEST_FLUSH", "BEFORE_MANIFEST_REPLACE", "AFTER_COMMIT"})
    void abruptlyHaltedJvmRecoversOnlyCommittedBytes(String boundary) throws Exception {
        var initial = store(5); save(initial, "A");
        Path next = temp.resolve("next.yml"); Files.write(next, source("B").bytes());
        Set<String> entries = new LinkedHashSet<>();
        for (Class<?> type : List.of(RecoveryCrashFixture.class, RevisionStore.class,
                com.fasterxml.jackson.core.JsonFactory.class, com.fasterxml.jackson.databind.ObjectMapper.class,
                com.fasterxml.jackson.annotation.JsonCreator.class, org.yaml.snakeyaml.Yaml.class))
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(javaExecutable, "-cp", String.join(File.pathSeparator, entries), RecoveryCrashFixture.class.getName(),
            temp.resolve("storage").toString(), boundary, next.toString()).redirectErrorStream(true).redirectOutput(temp.resolve("child.log").toFile()).start();
        try {
            assertTrue(child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "Crash fixture timed out");
            assertEquals(boundary.equals("AFTER_COMMIT") ? 74 : 73, child.exitValue(), Files.readString(temp.resolve("child.log")));
            assertEquals(source(boundary.equals("AFTER_COMMIT") ? "B" : "A").compile(), store(5).recover("hello-ai").compiled());
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
    @Test void reducedRetentionProtectsRecoveredOlderRevision() throws Exception {
        var initial = store(5); save(initial, "A"); save(initial, "B"); save(initial, "C");
        for (var revision : initial.state().features().get("hello-ai").revisions())
            if (revision.number() > 1) Files.writeString(sourcePath(revision), "damaged");
        edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        var reduced = store(1); var startup = manager(reduced).startup(); reduced.commit(startup.durable()); reduced.prune();
        assertEquals(1, reduced.state().features().get("hello-ai").active());
        assertEquals(source("A").compile(), store(1).recover("hello-ai").compiled());
    }
    @Test void compensatingRestoreReturnsDurablePointerToPriorState() throws Exception {
        var store = store(5); save(store, "A"); var before = store.state(); save(store, "B");
        // Production restoration happens before pruning; this test keeps both files (retention 5).
        store.restore(before);
        assertEquals(source("A").compile(), store(5).recover("hello-ai").compiled());
    }
    @Test void stalePreparedStateCannotOverwriteNewerCommit() throws Exception {
        var store = store(5); save(store, "A");
        var b = store.prepare(Map.of("hello-ai", source("B")), Set.of()); var c = store.prepare(Map.of("hello-ai", source("C")), Set.of());
        store.commit(b); assertThrows(IOException.class, () -> store.commit(c));
        assertEquals(source("B").compile(), store(5).recover("hello-ai").compiled());
    }
    @Test void validateHasNoPersistentSideEffectsAndInvalidReloadPreservesSnapshot() throws Exception {
        var store = store(5); edit(source("A").text()); var manager = manager(store); var registry = new Registry();
        assertEquals(1, manager.validate(null, registry.snapshot())); assertFalse(Files.exists(temp.resolve("storage/manifest.json")));
        apply(manager.reload(null, registry.snapshot()), store, registry); var before = registry.snapshot(); var durable = store.state();
        edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        var error = assertThrows(FeatureManager.Rejected.class, () -> manager.reload("hello-ai", before));
        assertTrue(error.diagnostics().stream().anyMatch(s -> s.contains("scripts/hello-ai.yml")));
        assertSame(before, registry.snapshot()); assertSame(durable, store.state());
    }
    @Test void statusHistoryAndDiffIdentifyOneEditTarget() throws Exception {
        var store = store(5); save(store, "A"); var manager = manager(store);
        var registry = new Registry(); registry.replace(0, Map.of("hello-ai", source("A").compile())); edit(source("B").text());
        assertTrue(manager.status("hello-ai", registry.snapshot()).stream().anyMatch(s -> s.contains("unapplied changes")));
        var diff = manager.diff("hello-ai"); assertTrue(diff.stream().anyMatch(s -> s.startsWith("- ") && s.contains("A")));
        assertTrue(diff.stream().anyMatch(s -> s.startsWith("+ ") && s.contains("B")));
        assertTrue(manager.history("hello-ai").stream().anyMatch(s -> s.contains("#1 [ACTIVE]")));
        edit(EngineTest.YAML.replace("target: sender", "target: everyone"));
        assertTrue(manager.list(registry.snapshot()).stream().anyMatch(s -> s.contains("file has errors")));
    }
    @Test void missingEditableFileRetainsSavedOnStartupButFullReloadCanRetireIt() throws Exception {
        var store = store(5); save(store, "A"); var manager = manager(store);
        var registry = new Registry(); apply(manager.startup(), store, registry);
        assertEquals(1, registry.snapshot().features().size());
        apply(manager.reload(null, registry.snapshot()), store, registry);
        assertTrue(registry.snapshot().features().isEmpty()); assertTrue(manager(store(5)).startup().target().isEmpty());
    }
    @Test void sourceAndConfigurationPathsCannotEscape() throws Exception {
        var store = store(5);
        assertThrows(ValidationException.class, () -> store.prepare(Map.of("../escape", source("A")), Set.of()));
        assertThrows(ValidationException.class, () -> store.recover("../escape"));
        Files.createDirectory(temp.resolve("config.yml")); assertThrows(IOException.class, () -> EngineSettings.load(temp.resolve("config.yml")));
        assertThrows(IllegalArgumentException.class, () -> store(0)); assertThrows(IllegalArgumentException.class, () -> store(51));
    }
    @Test void directoryMasqueradingAsSourceIsRejected() throws Exception {
        var store = store(5); save(store, "A"); Path file = sourcePath(store.state().features().get("hello-ai").activeRevision());
        Files.delete(file); Files.createDirectory(file); assertThrows(IOException.class, () -> store.recover("hello-ai"));
    }
    @Test void strictOwnerSettingsValidateBoundsAndDuplicates() throws Exception {
        Path file = temp.resolve("config.yml"); Files.writeString(file, "configVersion: 1\nretainVersions: 5\n");
        assertEquals(5, EngineSettings.load(file).retainVersions());
        for (String invalid : List.of("configVersion: 1\nretainVersions: 0", "configVersion: 1\nretainVersions: 51", "configVersion: 1\nretainVersions: 5\nretainVersions: 6", "configVersion: 1\nretainVersions: 5\nextra: 1", "configVersion: 1\nretainVersions: &a 5")) {
            Files.writeString(file, invalid); assertThrows(IOException.class, () -> EngineSettings.load(file));
        }
    }
    @Test void yamlAndJsonDiagnosticsCarryFieldLocationAndFix() throws Exception {
        var yaml = assertThrows(ValidationException.class, () -> new FeatureParser().parse(EngineTest.YAML.replace("target: sender", "target: everyone").getBytes(), "yaml"));
        assertEquals(11, yaml.line()); assertTrue(yaml.column() > 0); assertEquals("$.commands[0].actions[0].target", yaml.path());
        assertTrue(yaml.reason().contains("Allowed: sender"));
        String json = Files.readString(Path.of("../examples/hello-ai.json")).replace("\"target\": \"sender\"", "\"target\": \"everyone\"");
        var error = assertThrows(ValidationException.class, () -> new FeatureParser().parse(json.getBytes(), "json"));
        assertTrue(error.line() > 1); assertTrue(error.column() > 0); assertEquals(yaml.path(), error.path());
    }
}
