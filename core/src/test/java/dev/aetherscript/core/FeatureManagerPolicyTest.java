package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureManagerPolicyTest {
    @TempDir Path root;
    String source(String message) { return "feature hello version \"1.0.0\"\ncommand hello permission \"test.hello\" sender any { send \"" + message + "\" }\n"; }
    @Test void unauthorizedCollidingFileDoesNotDisableAnAuthorizedFirstInstall() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts"));
        Files.writeString(scripts.resolve("hello.aether"), source("still works"));
        Files.writeString(scripts.resolve("blocked.aether"), source("not approved").replace("feature hello", "feature blocked"));
        var store = new RevisionStore(root.resolve("storage"), 5);
        var manager = new FeatureManager(new ScriptStore(scripts), store, feature -> {
            if (feature.definition().id().equals("blocked")) throw new ValidationException("$.requires", "not owner approved");
        });
        assertEquals(Set.of("hello"), manager.startup().target().keySet());
    }
    @Test void everyPreparationChecksPolicyBeforeCreatingDurableChange() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts")); var store = new RevisionStore(root.resolve("storage"), 5);
        Files.writeString(scripts.resolve("hello.aether"), source("first"));
        var base = new FeatureManager(new ScriptStore(scripts), store); var registry = new Registry();
        var change = base.reload(null, registry.snapshot()); store.commit(change.durable()); registry.replace(0, change.target());
        Files.writeString(scripts.resolve("hello.aether"), source("second"));
        change = base.reload(null, registry.snapshot()); store.commit(change.durable()); registry.replace(1, change.target());
        var state = store.state();
        var denied = new FeatureManager(new ScriptStore(scripts), store, f -> { if (f.definition().id().equals("hello")) throw new ValidationException("$.requires", "owner denied"); });
        assertThrows(ValidationException.class, () -> denied.validateText("hello", source("third"), registry.snapshot()));
        assertThrows(ValidationException.class, () -> denied.prepareText("hello", source("third"), registry.snapshot()));
        assertThrows(ValidationException.class, () -> denied.reload(null, registry.snapshot()));
        assertThrows(ValidationException.class, () -> denied.validate(null, registry.snapshot()));
        assertThrows(ValidationException.class, () -> denied.rollback("hello", registry.snapshot()));
        assertSame(state, store.state());
        Files.writeString(scripts.resolve("neighbor.aether"), source("still running").replace("feature hello", "feature neighbor").replace("command hello", "command neighbor"));
        assertEquals(Set.of("neighbor"), denied.startup().target().keySet());
    }
    @Test void unauthorizedEditableAndNewestSavedRevisionFallBackToEarlierAuthorizedSource() throws Exception {
        Path scripts = Files.createDirectory(root.resolve("scripts")); var store = new RevisionStore(root.resolve("storage"), 5);
        var base = new FeatureManager(new ScriptStore(scripts), store); var registry = new Registry();
        for (String text : List.of("authorized", "denied")) {
            Files.writeString(scripts.resolve("hello.aether"), source(text)); var change = base.reload(null, registry.snapshot()); store.commit(change.durable()); registry.replace(registry.snapshot().generation(), change.target());
        }
        String authorizedHash = new FeatureParser().parse(source("authorized").getBytes(java.nio.charset.StandardCharsets.UTF_8), "aether").sourceHash();
        var manager = new FeatureManager(new ScriptStore(scripts), store, f -> { if (!f.sourceHash().equals(authorizedHash)) throw new ValidationException("$.requires", "owner denied"); });
        var startup = manager.startup(); assertEquals(authorizedHash, startup.target().get("hello").sourceHash());
        assertTrue(startup.messages().stream().anyMatch(m -> m.contains("scripts/hello.aether")));
        assertEquals(source("denied"), Files.readString(scripts.resolve("hello.aether")));
    }
}
