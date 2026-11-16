package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import java.util.*;
import java.util.concurrent.Callable;

/** A closed operation vocabulary; never dispatches commands or creates a Bukkit sender. */
final class RemoteScripts {
    interface Main { <T> T call(Callable<T> action) throws Exception; }
    interface Activate { void apply(Registry.Snapshot before, FeatureManager.Change change, Runnable authorized) throws Exception; }
    interface NativeAccess {
        void authorize(CompiledFeature feature) throws Exception;
        Map<String, Object> start(CompiledFeature feature, List<String> arguments, List<String> expected) throws Exception;
        Map<String, Object> status(String featureId, String testId) throws Exception;
        default boolean testPlayerApproved() { return false; }
        default Map<String, Object> describe() { return Map.of(); }
    }
    private final NativeAccess nativeAccess;
    private final FeatureManager manager;
    private final Registry registry;
    private final Set<String> allowed;
    private final boolean allScripts;
    private final Main main;
    private final Activate activate;
    private volatile boolean revoked;
    RemoteScripts(FeatureManager manager, Registry registry, Set<String> allowed, Main main, Activate activate) {
        this(manager, registry, allowed, false, main, activate);
    }
    RemoteScripts(FeatureManager manager, Registry registry, Set<String> allowed, boolean allScripts, Main main, Activate activate) {
        this(manager, registry, allowed, allScripts, main, activate, null);
    }
    RemoteScripts(FeatureManager manager, Registry registry, Set<String> allowed, boolean allScripts, Main main, Activate activate, NativeAccess nativeAccess) {
        this.nativeAccess = nativeAccess;
        allowed.forEach(FeatureParser::requireId);
        this.manager = manager; this.registry = registry; this.allowed = Set.copyOf(allowed); this.allScripts = allScripts; this.main = main; this.activate = activate;
    }
    void revoke() { revoked = true; }
    private void authorized() { if (revoked) throw new SecurityException("REVOKED"); }
    synchronized Map<String, Object> execute(String operation, Map<String, Object> args) throws Exception {
        authorized();
        if (operation.equals("status")) { exact(args, Set.of()); return Map.of("generation", registry.snapshot().generation(), "scope", "manage", "targets", allowed, "allScripts", allScripts); }
        if (operation.equals("scripts.list")) {
            exact(args, Set.of()); Set<String> ids = new TreeSet<>(allowed);
            if (allScripts) for (var file : manager.scripts().inspect(null).values()) {
                if (!Arrays.asList(file.fileName().split(" \\+ ")).contains(file.id() + ".aether")) continue;
                try { FeatureParser.requireId(file.id()); ids.add(file.id()); } catch (ValidationException ignored) { }
            }
            return Map.of("ids", List.copyOf(ids), "allScripts", allScripts);
        }
        if (operation.equals("capabilities")) {
            exact(args, Set.of());
            return Map.of("authoring", AuthoringGuide.metadata(), "native", nativeAccess == null ? Map.of("enabled", false) : nativeAccess.describe());
        }
        String id = string(args, "id"); FeatureParser.requireId(id);
        if (!allScripts && !allowed.contains(id)) throw new SecurityException("TARGET_DENIED");
        switch (operation) {
            case "scripts.read" -> { exact(args, Set.of("id")); return read(id); }
            case "scripts.validate" -> {
                exact(args, Set.of("id", "source")); var compiled = manager.validateText(id, string(args, "source"), registry.snapshot());
                authorize(compiled);
                return Map.of("valid", true, "hash", compiled.sourceHash());
            }
            case "scripts.apply" -> {
                exact(args, Set.of("id", "source", "expectedHash"));
                var before = registry.snapshot(); String source = string(args, "source"), expectedHash = string(args, "expectedHash");
                var candidate = manager.validateText(id, source, before);
                authorize(candidate);
                var previous = textSource(id);
                if (!Objects.equals(expectedHash, previous == null ? "ABSENT" : previous.hash())) throw new java.io.IOException("STALE_SOURCE");
                authorized(); FeatureManager.Change change = manager.prepareText(id, source, before);
                boolean needsIntent = change.durable().changed();
                if (needsIntent) manager.scripts().beginRemoteIntent(id, expectedHash, source, change.durable().before().sequence());
                try { authorized(); activate.apply(before, change, () -> {
                    authorized();
                    try { authorize(candidate); }
                    catch (RuntimeException failure) { throw failure; }
                    catch (Exception failure) { throw new SecurityException("REMOTE_AUTHORIZATION_UNAVAILABLE", failure); }
                }); }
                catch (Exception failure) {
                    boolean committed = change.durable().changed() && manager.store().state().equals(change.durable().after());
                    if (!committed) { if (needsIntent) manager.scripts().finishRemoteIntent(id); throw failure; }
                    // Delivery/logging failure after commit must never roll a committed source back.
                }
                if (!needsIntent) { authorized(); authorize(candidate); }
                try { manager.scripts().compareAndSwapText(id, expectedHash, source); }
                catch (java.io.IOException conflict) {
                    if (needsIntent && ("STALE_SOURCE".equals(conflict.getMessage()) || "TEXT_SOURCE_REQUIRED".equals(conflict.getMessage()))) manager.scripts().finishRemoteIntent(id);
                    throw conflict;
                }
                if (needsIntent) manager.scripts().finishRemoteIntent(id);
                return read(id);
            }
            case "scripts.test.status" -> {
                exact(args, Set.of("id", "testId"));
                if (nativeAccess == null) throw new SecurityException("REMOTE_CAPABILITY_DENIED");
                return nativeAccess.status(id, string(args, "testId"));
            }
            case "scripts.test" -> {
                Set<String> fields = new HashSet<>(args.keySet()); fields.remove("arguments");
                if (!fields.equals(Set.of("id", "expectedMessages"))) throw new IllegalArgumentException("INVALID_ARGUMENTS");
                List<String> invocation = invocationArguments(args);
                if (!(args.get("expectedMessages") instanceof List<?> expected) || expected.size() > 128 || expected.stream().anyMatch(v -> !(v instanceof String)))
                    throw new IllegalArgumentException("INVALID_EXPECTED_MESSAGES");
                var candidate = registry.snapshot().features().get(id);
                if (nativeAccess != null && candidate != null &&
                    (nativeAccess.testPlayerApproved() || candidate.definition().command().sender().equals("player") ||
                    candidate.definition().requires().stream().anyMatch(capability -> !capability.equals(ScriptCapabilityPolicy.MESSAGE)))) {
                    authorize(candidate); manager.authorize(candidate);
                    return nativeAccess.start(candidate, invocation, expected.stream().map(String.class::cast).toList());
                }
                return main.call(() -> {
                    authorized();
                    var active = registry.snapshot().features().get(id);
                    if (active != null) { messageOnly(active); manager.authorize(active); }
                    List<String> actual = new ArrayList<>();
                    // This synthetic capability can exercise only non-player message actions.
                    boolean invoked = registry.run(id, invocation, false, permission -> !revoked, actual::add);
                    return Map.of("invoked", invoked, "actualMessages", List.copyOf(actual), "matched", invoked && actual.equals(expected), "sender", "synthetic-agent", "generation", registry.snapshot().generation());
                });
            }
            default -> throw new SecurityException("OPERATION_DENIED");
        }
    }
    private void authorize(CompiledFeature compiled) throws Exception {
        if (nativeAccess != null) nativeAccess.authorize(compiled);
        else messageOnly(compiled);
    }
    private static void messageOnly(CompiledFeature compiled) {
        if (compiled.definition().requires().stream().anyMatch(capability -> !capability.equals(ScriptCapabilityPolicy.MESSAGE)))
            throw new SecurityException("REMOTE_CAPABILITY_DENIED: remote grants permit message-only scripts");
    }
    private Map<String, Object> read(String id) throws Exception {
        authorized(); var source = textSource(id); var snapshot = registry.snapshot(); var active = snapshot.features().get(id);
        var history = manager.store().state().features().get(id);
        return Map.of("id", id, "exists", source != null, "source", source == null ? "" : source.text(), "hash", source == null ? "ABSENT" : source.hash(),
            "activeHash", active == null ? "" : active.sourceHash(), "generation", snapshot.generation(), "revision", history == null ? 0 : history.active());
    }
    private SourceFile textSource(String id) throws java.io.IOException {
        try { return manager.scripts().textSource(id); }
        catch (java.io.IOException failure) {
            if ("TEXT_SOURCE_REQUIRED".equals(failure.getMessage())) throw new java.io.IOException("TEXT_SOURCE_REQUIRED: owner must convert this ID to a single safe .aether file first; legacy YAML/JSON and duplicate sources are never overwritten.");
            throw failure;
        }
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
