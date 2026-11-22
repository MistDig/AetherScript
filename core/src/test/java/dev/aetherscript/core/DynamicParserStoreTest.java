package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class DynamicParserStoreTest {
    private static final String SOURCE = "feature addon version \"1\"\ncommand addon permission \"addon.use\" sender player {\nlet result = addon_echo(\"hello\")\nsend result\n}\n";
    private FeatureParser parser() {
        var registry = StandardCallables.create();
        registry.register(new CallableRegistry.Descriptor("example.echo", "addon_echo", List.of(ScriptType.TEXT), ScriptType.TEXT,
            CallableRegistry.ExecutionContext.PAPER_MAIN, Set.of("example.echo"), true, "test", "rejected", "Echo test", "example"));
        return new FeatureParser(registry);
    }
    @Test void suppliedCatalogReachesSourceRemoteInspectionAndSavedRecovery(@TempDir Path root) throws Exception {
        var enabled = new AtomicBoolean(true);
        Supplier<FeatureParser> factory = () -> enabled.get() ? parser() : new FeatureParser();
        var scripts = Files.createDirectory(root.resolve("scripts"));
        var source = new SourceFile("addon.aether", "aether", SOURCE.getBytes(StandardCharsets.UTF_8));
        assertThrows(ValidationException.class, source::compile);
        assertEquals("addon", source.compile(parser()).definition().id());
        var editable = new ScriptStore(scripts, factory);
        var revisions = new RevisionStore(root.resolve("storage"), 5, factory);
        var manager = new FeatureManager(editable, revisions);
        var empty = new Registry().snapshot();
        assertEquals("addon", manager.validateText("addon", SOURCE, empty).definition().id());
        var change = manager.prepareText("addon", SOURCE, empty);
        revisions.commit(change.durable());
        editable.beginRemoteIntent("addon", "ABSENT", SOURCE, 0);
        editable.recoverRemoteIntents(revisions);
        assertTrue(editable.inspect("addon").get("addon").valid());
        assertEquals(SOURCE, editable.textSource("addon").text());
        assertEquals("addon", revisions.recover("addon").compiled().definition().id());
        enabled.set(false);
        assertFalse(editable.inspect("addon").get("addon").valid());
        assertThrows(ValidationException.class, () -> manager.validateText("addon", SOURCE, empty));
        assertThrows(java.io.IOException.class, () -> revisions.recover("addon"));
    }
    @Test void prepareUsesOneSnapshotForValidationAndDurableRecompile(@TempDir Path root) throws Exception {
        var count = new AtomicInteger();
        Supplier<FeatureParser> once = () -> count.getAndIncrement() == 0 ? parser() : new FeatureParser();
        var scripts = new ScriptStore(Files.createDirectory(root.resolve("scripts")), once);
        var revisions = new RevisionStore(root.resolve("storage"), 5, () -> { fail("Manager must forward the same parser snapshot"); return null; });
        var change = new FeatureManager(scripts, revisions).prepareText("addon", SOURCE, new Registry().snapshot());
        assertTrue(change.durable().changed()); assertEquals(1, count.get());
    }
    @Test void packageSnapshotUsesSuppliedCatalogThroughRevisionStorage(@TempDir Path root) throws Exception {
        var pack = Files.createDirectory(root.resolve("addon.aetherpack"));
        Files.writeString(pack.resolve("main.aether"), SOURCE);
        var source = AetherPackage.read(pack);
        assertThrows(ValidationException.class, source::compile);
        var revisions = new RevisionStore(root.resolve("storage"), 5, this::parser);
        revisions.commit(revisions.prepare(Map.of("addon", source), Set.of()));
        assertEquals("addon", revisions.recover("addon").compiled().definition().id());
    }
}
