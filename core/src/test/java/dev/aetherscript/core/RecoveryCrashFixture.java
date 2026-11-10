package dev.aetherscript.core;

import java.nio.file.*;
import java.util.*;

/** Separate JVM intentionally halts without finally blocks or shutdown hooks. */
public final class RecoveryCrashFixture {
    public static void main(String[] args) throws Exception {
        var store = new RevisionStore(Path.of(args[0]), 5, boundary -> {
            if (boundary.equals(args[1])) Runtime.getRuntime().halt(73);
        });
        var source = new SourceFile("hello-ai.yml", "yml", Files.readAllBytes(Path.of(args[2])));
        store.commit(store.prepare(Map.of("hello-ai", source), Set.of()));
        Runtime.getRuntime().halt(74);
    }
}
