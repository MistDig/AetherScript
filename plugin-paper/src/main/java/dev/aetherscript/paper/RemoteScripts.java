package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import java.util.*;
import java.util.concurrent.Callable;

/** A closed operation vocabulary; never dispatches commands or creates a Bukkit sender. */
final class RemoteScripts {
    interface Main { <T> T call(Callable<T> action) throws Exception; }
    interface Activate { void apply(Registry.Snapshot before, FeatureManager.Change change, Runnable authorized) throws Exception; }
    private final FeatureManager manager;
    private final Registry registry;
    private final Set<String> allowed;
    private final Main main;
    private final Activate activate;
    private volatile boolean revoked;
    RemoteScripts(FeatureManager manager, Registry registry, Set<String> allowed, Main main, Activate activate) {
        allowed.forEach(FeatureParser::requireId);
        this.manager = manager; this.registry = registry; this.allowed = Set.copyOf(allowed); this.main = main; this.activate = activate;
    }
    void revoke() { revoked = true; }
    private void authorized() { if (revoked) throw new SecurityException("REVOKED"); }
    synchronized Map<String, Object> execute(String operation, Map<String, Object> args) throws Exception {
        authorized();
        if (operation.equals("status")) { exact(args, Set.of()); return Map.of("generation", registry.snapshot().generation(), "scope", "manage", "targets", allowed); }
        if (operation.equals("scripts.list")) { exact(args, Set.of()); return Map.of("ids", allowed.stream().sorted().toList()); }
        String id = string(args, "id"); FeatureParser.requireId(id);
        if (!allowed.contains(id)) throw new SecurityException("TARGET_DENIED");
        switch (operation) {
            case "scripts.read" -> { exact(args, Set.of("id")); return read(id); }
            case "scripts.validate" -> {
                exact(args, Set.of("id", "source")); var compiled = manager.validateText(id, string(args, "source"), registry.snapshot());
                return Map.of("valid", true, "hash", compiled.sourceHash());
            }
            case "scripts.apply" -> {
                exact(args, Set.of("id", "source", "expectedHash"));
                var before = registry.snapshot(); String source = string(args, "source"), expectedHash = string(args, "expectedHash");
                var candidate = manager.validateText(id, source, before);
                var previous = manager.scripts().textSource(id);
                if (!Objects.equals(expectedHash, previous == null ? "ABSENT" : previous.hash())) throw new java.io.IOException("STALE_SOURCE");
                authorized(); FeatureManager.Change change = manager.prepareText(id, source, before);
                boolean needsIntent = change.durable().changed();
                if (needsIntent) manager.scripts().beginRemoteIntent(id, expectedHash, source, change.durable().before().sequence());
                try { authorized(); activate.apply(before, change, this::authorized); }
                catch (Exception failure) {
                    boolean committed = change.durable().changed() && manager.store().state().equals(change.durable().after());
                    if (!committed) { if (needsIntent) manager.scripts().finishRemoteIntent(id); throw failure; }
                    // Delivery/logging failure after commit must never roll a committed source back.
                }
                if (!needsIntent) authorized();
                try { manager.scripts().compareAndSwapText(id, expectedHash, source); }
                catch (java.io.IOException conflict) {
                    if (needsIntent && ("STALE_SOURCE".equals(conflict.getMessage()) || "TEXT_SOURCE_REQUIRED".equals(conflict.getMessage()))) manager.scripts().finishRemoteIntent(id);
                    throw conflict;
                }
                if (needsIntent) manager.scripts().finishRemoteIntent(id);
                return read(id);
            }
            case "scripts.test" -> {
                Set<String> fields = new HashSet<>(args.keySet()); fields.remove("arguments");
                if (!fields.equals(Set.of("id", "expectedMessages"))) throw new IllegalArgumentException("INVALID_ARGUMENTS");
                List<String> invocation = invocationArguments(args);
                if (!(args.get("expectedMessages") instanceof List<?> expected) || expected.size() > 128 || expected.stream().anyMatch(v -> !(v instanceof String)))
                    throw new IllegalArgumentException("INVALID_EXPECTED_MESSAGES");
                return main.call(() -> {
                    authorized(); List<String> actual = new ArrayList<>();
                    // This synthetic capability can exercise only non-player message actions.
                    boolean invoked = registry.run(id, invocation, false, permission -> !revoked, actual::add);
                    return Map.of("invoked", invoked, "actualMessages", List.copyOf(actual), "matched", invoked && actual.equals(expected), "sender", "synthetic-agent", "generation", registry.snapshot().generation());
                });
            }
            default -> throw new SecurityException("OPERATION_DENIED");
        }
    }
    private Map<String, Object> read(String id) throws Exception {
        authorized(); var source = manager.scripts().textSource(id); var snapshot = registry.snapshot(); var active = snapshot.features().get(id);
        var history = manager.store().state().features().get(id);
        return Map.of("id", id, "exists", source != null, "source", source == null ? "" : source.text(), "hash", source == null ? "ABSENT" : source.hash(),
            "activeHash", active == null ? "" : active.sourceHash(), "generation", snapshot.generation(), "revision", history == null ? 0 : history.active());
    }
    private static List<String> invocationArguments(Map<String, Object> args) {
        if (!args.containsKey("arguments")) return List.of();
        if (!(args.get("arguments") instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof String)))
            throw new IllegalArgumentException("INVALID_ARGUMENTS");
        return InvocationArguments.checked(values.stream().map(String.class::cast).toList());
    }
    private static String string(Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof String text)) throw new IllegalArgumentException("INVALID_ARGUMENT: " + key); return text;
    }
    private static void exact(Map<String, Object> args, Set<String> fields) {
        if (!args.keySet().equals(fields)) throw new IllegalArgumentException("INVALID_ARGUMENTS");
    }
}
