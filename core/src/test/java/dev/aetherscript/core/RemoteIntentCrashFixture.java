package dev.aetherscript.core;
import java.nio.file.*;
import java.util.*;

/** Halts at actual remote transaction boundaries without cleanup. */
public final class RemoteIntentCrashFixture {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); String next = Files.readString(Path.of(args[1]));
        var scripts = new ScriptStore(root.resolve("scripts")); var store = new RevisionStore(root.resolve("storage"), 5);
        var manager = new FeatureManager(scripts, store); var registry = new Registry();
        var initial = manager.startup(); registry.replace(0, initial.target()); store.commit(initial.durable());
        if (args[2].equals("sameHashBeforeCommit")) {
            next = Files.readString(root.resolve("scripts/hello-ai.aether"));
            Files.writeString(root.resolve("scripts/hello-ai.aether"), next.replace("\"Hello\"", "\"Owner pending\""));
        }
        String expected = scripts.textSource("hello-ai").hash();
        scripts.beginRemoteIntent("hello-ai", expected, next, store.state().sequence());
        var change = manager.prepareText("hello-ai", next, registry.snapshot());
        registry.replace(registry.snapshot().generation(), change.target());
        if (args[2].equals("beforeCommit") || args[2].equals("sameHashBeforeCommit")) Runtime.getRuntime().halt(73);
        store.commit(change.durable());
        if (args[2].equals("afterCommit")) Runtime.getRuntime().halt(73);
        scripts.compareAndSwapText("hello-ai", expected, next);
        Runtime.getRuntime().halt(73);
    }
}
