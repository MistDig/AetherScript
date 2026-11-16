package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;

class RemoteScriptsTest {
    @TempDir Path root;
    private String source() throws Exception { return Files.readString(Path.of("../examples/hello-ai.aether")); }
    private RemoteScripts service(FeatureManager manager, Registry registry) {
        return new RemoteScripts(manager, registry, Set.of("hello-ai"), new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> {
            authorized.run(); registry.replace(before.generation(), change.target()); manager.store().commit(change.durable());
        });
    }
    @Test void allScriptsAndOwnerApprovedCapabilitiesNeverExpandRemoteMessageOnlyGrant() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        var policy = new ScriptCapabilityPolicy(root.resolve("policy.json"), Set.of(ScriptCapabilityPolicy.MESSAGE, "player.location.read", "state.own", "scheduler.delay", "player.teleport.self"));
        policy.grant("hello-ai", Set.of(ScriptCapabilityPolicy.MESSAGE, "player.location.read", "state.own", "scheduler.delay", "player.teleport.self"));
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5), policy::validate);
        var registry = new Registry();
        var service = new RemoteScripts(manager, registry, Set.of(), true, new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> { fail("Remote native source must never reach activation"); });
        for (String body : List.of("let home = player_location()", "delay(20)", "let home = player_location()\n state_put_location(\"home\", home)", "let home = player_location()\n teleport_self(home)")) {
            String nativeSource = "feature hello-ai version \"1.0.0\"\ncommand helloai permission \"test.hello\" sender player {\n" + body + "\n}\n";
            assertTrue(manager.validateText("hello-ai", nativeSource, registry.snapshot()).definition().requires().size() > 1);
            assertThrows(SecurityException.class, () -> service.execute("scripts.validate", Map.of("id", "hello-ai", "source", nativeSource)));
            assertThrows(SecurityException.class, () -> service.execute("scripts.apply", Map.of("id", "hello-ai", "source", nativeSource, "expectedHash", "ABSENT")));
            assertFalse(Files.exists(scripts.resolve("hello-ai.aether"))); assertEquals(0, manager.store().state().sequence());
        }
        String nativeSource = "feature hello-ai version \"1.0.0\"\ncommand helloai permission \"test.hello\" sender player { let home = player_location() }\n";
        registry.replace(0, Map.of("hello-ai", manager.validateText("hello-ai", nativeSource, registry.snapshot())));
        assertThrows(SecurityException.class, () -> service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of(), "sender", "player")));
    }
    @Test void liveSyntheticTestsBindRuntimeArgumentsBeforeEvaluatingBranches() throws Exception {
        var manager = new FeatureManager(new ScriptStore(Files.createDirectory(root.resolve("scripts"))), new RevisionStore(root.resolve("storage"), 5));
        var registry = new Registry(); var service = service(manager, registry);
        String runtime = Files.readString(Path.of("../examples/greet.aether")).replace("feature greet", "feature hello-ai").replace("command greet", "command helloai");
        service.execute("scripts.apply", Map.of("id", "hello-ai", "source", runtime, "expectedHash", "ABSENT"));
        var result = service.execute("scripts.test", Map.of("id", "hello-ai", "arguments", List.of("Alex", "3", "false"), "expectedMessages", List.of("Hello Alex. Count 3")));
        assertEquals(true, result.get("matched")); assertEquals(List.of("Hello Alex. Count 3"), result.get("actualMessages"));
        var defaults = service.execute("scripts.test", Map.of("id", "hello-ai", "arguments", List.of("Sam"), "expectedMessages", List.of("Hello Sam! Count 1")));
        assertEquals(true, defaults.get("matched"));
        var missing = assertThrows(ValidationException.class, () -> service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of())));
        assertEquals("$.arguments[0]", missing.path());
        assertThrows(IllegalArgumentException.class, () -> service.execute("scripts.test", Map.of("id", "hello-ai", "arguments", List.of(1), "expectedMessages", List.of())));
    }
    @Test void createEditReadbackActualMessagesStaleAndRevocation() throws Exception {
        var manager = new FeatureManager(new ScriptStore(Files.createDirectory(root.resolve("scripts"))), new RevisionStore(root.resolve("storage"), 5));
        var registry = new Registry(); var service = service(manager, registry);
        var absent = service.execute("scripts.read", Map.of("id", "hello-ai")); assertEquals("ABSENT", absent.get("hash"));
        var created = service.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT"));
        assertEquals(created.get("hash"), created.get("activeHash")); assertEquals(source(), created.get("source"));
        var test = service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of("Hello from AetherScript!")));
        assertEquals(List.of("Hello from AetherScript!"), test.get("actualMessages")); assertEquals(true, test.get("matched"));
        assertEquals(false, service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of("invented"))).get("matched"));
        assertThrows(java.io.IOException.class, () -> service.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT")));
        String edited = source().replace("let greeting = \"Hello\"", "let greeting = \"Edited\"");
        var changed = service.execute("scripts.apply", Map.of("id", "hello-ai", "source", edited, "expectedHash", created.get("hash")));
        assertNotEquals(created.get("hash"), changed.get("hash"));
        assertEquals(List.of("Edited from AetherScript!"), service.execute("scripts.test", Map.of("id", "hello-ai", "expectedMessages", List.of())).get("actualMessages"));
        service.revoke(); assertThrows(SecurityException.class, () -> service.execute("scripts.read", Map.of("id", "hello-ai")));
    }
    @Test void targetPathSourceTypeAndOperationBypassesAreRejected() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5));
        var service = service(manager, new Registry());
        assertThrows(SecurityException.class, () -> service.execute("scripts.read", Map.of("id", "other")));
        assertThrows(ValidationException.class, () -> service.execute("scripts.read", Map.of("id", "../hello-ai")));
        assertThrows(SecurityException.class, () -> service.execute("console", Map.of("id", "hello-ai")));
        assertThrows(IllegalArgumentException.class, () -> service.execute("scripts.read", Map.of("id", "hello-ai", "sender", "Steve")));
        assertThrows(ValidationException.class, () -> service.execute("scripts.apply", Map.of("id", "hello-ai", "source", "java.lang.Runtime.exec(\"bad\")", "expectedHash", "ABSENT")));
        assertFalse(Files.exists(scripts.resolve("hello-ai.aether")));
        Files.writeString(scripts.resolve("hello-ai.yml"), "owner file");
        assertThrows(java.io.IOException.class, () -> service.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT")));
        assertEquals("owner file", Files.readString(scripts.resolve("hello-ai.yml")));
    }
    @Test void postCommitDeliveryFailureDoesNotRestoreOldEditableSource() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5)); var registry = new Registry();
        var service = new RemoteScripts(manager, registry, Set.of("hello-ai"), new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> {
            authorized.run(); registry.replace(before.generation(), change.target()); manager.store().commit(change.durable());
            throw new java.io.IOException("simulated reply delivery failure");
        });
        var result = service.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT"));
        assertEquals(source(), result.get("source")); assertEquals(result.get("hash"), result.get("activeHash"));
        assertEquals(source(), Files.readString(scripts.resolve("hello-ai.aether")));
    }
    @Test void rejectedActivationLeavesNoEditableSourceAndNoRecoveryIntent() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5));
        var service = new RemoteScripts(manager, new Registry(), Set.of("hello-ai"), new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> { throw new SecurityException("REVOKED"); });
        assertThrows(SecurityException.class, () -> service.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT")));
        assertFalse(Files.exists(scripts.resolve("hello-ai.aether")));
        assertTrue(manager.startup().target().isEmpty());
    }
    @Test void preexistingActiveHashDoesNotTurnDeniedActivationIntoPublication() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5)); var registry = new Registry();
        var initial = service(manager, registry).execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", "ABSENT"));
        String owner = source().replace("\"Hello\"", "\"Owner pending\""); Files.writeString(scripts.resolve("hello-ai.aether"), owner);
        String expected = manager.scripts().textSource("hello-ai").hash();
        var denied = new RemoteScripts(manager, registry, Set.of("hello-ai"), new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> { throw new SecurityException("REVOKED"); });
        assertThrows(SecurityException.class, () -> denied.execute("scripts.apply", Map.of("id", "hello-ai", "source", source(), "expectedHash", expected)));
        assertEquals(owner, Files.readString(scripts.resolve("hello-ai.aether")));
        assertEquals(initial.get("activeHash"), registry.snapshot().features().get("hello-ai").sourceHash());
    }
    @Test void fullNativeScopeListsInvalidAndInactiveSourcesCreatesNewIdsAndProtectsLegacy() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        Files.writeString(scripts.resolve("hello-ai.aether"), source());
        Files.writeString(scripts.resolve("broken.aether"), "invalid native source");
        Files.writeString(scripts.resolve("legacy.yml"), "owner legacy content");
        Files.writeString(scripts.resolve("mixed.aether"), "invalid native source");
        Files.writeString(scripts.resolve("mixed.yml"), "owner duplicate content");
        var manager = new FeatureManager(new ScriptStore(scripts), new RevisionStore(root.resolve("storage"), 5)); var registry = new Registry();
        var full = new RemoteScripts(manager, registry, Set.of(), true, new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return action.call(); }
        }, (before, change, authorized) -> { authorized.run(); registry.replace(before.generation(), change.target()); manager.store().commit(change.durable()); });
        assertEquals(List.of("broken", "hello-ai", "mixed"), full.execute("scripts.list", Map.of()).get("ids"));
        assertEquals("ABSENT", full.execute("scripts.read", Map.of("id", "new-note")).get("hash"));
        String next = source().replace("feature hello-ai", "feature new-note").replace("command helloai", "command newnote");
        var created = full.execute("scripts.apply", Map.of("id", "new-note", "source", next, "expectedHash", "ABSENT"));
        assertEquals(created.get("hash"), created.get("activeHash"));
        assertEquals(List.of("broken", "hello-ai", "mixed", "new-note"), full.execute("scripts.list", Map.of()).get("ids"));
        var legacy = assertThrows(java.io.IOException.class, () -> full.execute("scripts.read", Map.of("id", "legacy")));
        assertTrue(legacy.getMessage().startsWith("TEXT_SOURCE_REQUIRED:"));
        assertThrows(java.io.IOException.class, () -> full.execute("scripts.apply", Map.of("id", "legacy", "source", next.replace("feature new-note", "feature legacy").replace("command newnote", "command legacynote"), "expectedHash", "ABSENT")));
        assertEquals("owner legacy content", Files.readString(scripts.resolve("legacy.yml")));
        assertThrows(SecurityException.class, () -> service(manager, registry).execute("scripts.read", Map.of("id", "new-note")));
        full.revoke(); assertThrows(SecurityException.class, () -> full.execute("scripts.list", Map.of()));
    }
}
