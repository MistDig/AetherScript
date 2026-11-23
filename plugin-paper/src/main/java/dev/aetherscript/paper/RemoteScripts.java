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
                var names = Arrays.asList(file.fileName().split(" \\+ "));
                if (!names.contains(file.id() + ".aether") && !names.contains(file.id() + ".aetherpack")) continue;
                try { FeatureParser.requireId(file.id()); ids.add(file.id()); } catch (ValidationException ignored) { }
            }
            return Map.of("ids", List.copyOf(ids), "allScripts", allScripts);
        }
        if (operation.equals("capabilities")) {
            exact(args, Set.of());
            return Map.of("guide", FeatureParser.JSON.readTree(AiDocs.summary(BuildInfo.VERSION)),
                "native", nativeAccess == null ? Map.of("enabled", false) : nativeAccess.describe());
        }
        if (operation.equals("docs.read")) {
            exact(args, args.containsKey("version") ? Set.of("section", "version") : Set.of("section"));
            String version = args.containsKey("version") ? string(args, "version") : BuildInfo.VERSION;
            return FeatureParser.JSON.readValue(AiDocs.read(version, string(args, "section")), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
        }
        String id = string(args, "id"); FeatureParser.requireId(id);
        if (!allScripts && !allowed.contains(id)) throw new SecurityException("TARGET_DENIED");
        switch (operation) {
            case "scripts.read" -> { formatFields(args, Set.of("id")); return read(id, format(args)); }
            case "scripts.validate" -> {
                formatFields(args, Set.of("id", "source")); var compiled = manager.validateSource(id, format(args), string(args, "source"), registry.snapshot());
                authorize(compiled);
                return Map.of("valid", true, "hash", compiled.sourceHash());
            }
            case "scripts.apply" -> {
                formatFields(args, Set.of("id", "source", "expectedHash"));
                String format = format(args);
                var before = registry.snapshot(); String source = string(args, "source"), expectedHash = string(args, "expectedHash");
                var candidate = manager.validateSource(id, format, source, before);
                authorize(candidate);
                var previous = editableSource(id, format);
                if (!Objects.equals(expectedHash, previous == null ? "ABSENT" : previous.hash())) throw new java.io.IOException("STALE_SOURCE");
                authorized(); FeatureManager.Change change = manager.prepareSource(id, format, source, before);
                boolean needsIntent = change.durable().changed();
                if (needsIntent) manager.scripts().beginRemoteIntent(id, expectedHash, format, source, change.durable().before().sequence());
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
                try { manager.scripts().compareAndSwapSource(id, expectedHash, format, source); }
                catch (java.io.IOException conflict) {
                    if (needsIntent && ("STALE_SOURCE".equals(conflict.getMessage()) || "TEXT_SOURCE_REQUIRED".equals(conflict.getMessage()) || "PACKAGE_DIRECTORY_READ_ONLY".equals(conflict.getMessage()) || "SOURCE_FORMAT_MISMATCH".equals(conflict.getMessage()))) manager.scripts().finishRemoteIntent(id);
                    throw conflict;
                }
                if (needsIntent) manager.scripts().finishRemoteIntent(id);
                return read(id, format);
            }
            case "events.preview" -> {
                exact(args, Set.of("id", "event"));
                var candidate = registry.snapshot().features().get(id);
                if (candidate == null || candidate.program() == null) throw new IllegalArgumentException("EVENT_FEATURE_REQUIRED");
                authorize(candidate);
                manager.authorize(candidate);
                return previewEvent(candidate, args.get("event"));
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
    private Map<String, Object> read(String id, String format) throws Exception {
        authorized(); var source = editableSource(id, format); var snapshot = registry.snapshot(); var active = snapshot.features().get(id);
        var history = manager.store().state().features().get(id);
        return Map.of("id", id, "format", format, "exists", source != null, "source", source == null ? "" : source.text(), "hash", source == null ? "ABSENT" : source.hash(),
            "activeHash", active == null ? "" : active.sourceHash(), "generation", snapshot.generation(), "revision", history == null ? 0 : history.active());
    }
    private SourceFile editableSource(String id, String format) throws java.io.IOException {
        try { return manager.scripts().editableSource(id, format); }
        catch (java.io.IOException failure) {
            if ("TEXT_SOURCE_REQUIRED".equals(failure.getMessage())) throw new java.io.IOException("TEXT_SOURCE_REQUIRED: owner must convert this ID to a single safe .aether file first; legacy YAML/JSON and duplicate sources are never overwritten.");
            throw failure;
        }
    }
    private static void formatFields(Map<String, Object> args, Set<String> required) {
        Set<String> fields = new HashSet<>(args.keySet()); fields.remove("format");
        if (!fields.equals(required)) throw new IllegalArgumentException("INVALID_ARGUMENTS");
    }
    private static String format(Map<String, Object> args) {
        String format = args.containsKey("format") ? string(args, "format") : "aether";
        if (!Set.of("aether", "aetherpack").contains(format)) throw new IllegalArgumentException("SOURCE_FORMAT_UNSUPPORTED");
        return format;
    }
    private Map<String, Object> previewEvent(CompiledFeature feature, Object raw) {
        if (!(raw instanceof Map<?, ?> event) || !event.keySet().equals(Set.of("kind", "fields"))
            || !(event.get("kind") instanceof String kind) || !(event.get("fields") instanceof Map<?, ?> fields))
            throw new IllegalArgumentException("INVALID_EVENT_SNAPSHOT");
        var expected = CompiledFeature.eventFields(kind);
        if (!fields.keySet().equals(expected.keySet())) throw new IllegalArgumentException("INVALID_EVENT_FIELDS");
        Map<String, ScriptValue> typed = new LinkedHashMap<>();
        expected.forEach((name, type) -> {
            Object value = fields.get(name);
            if (type == ScriptType.INTEGER && (value instanceof Integer || value instanceof Long)) value = ((Number) value).longValue();
            try { typed.put(name, new ScriptValue(type, value)); }
            catch (IllegalArgumentException | NullPointerException failure) { throw new IllegalArgumentException("INVALID_EVENT_FIELD: " + name); }
        });
        var snapshot = new CompiledFeature.EventData(kind, typed);
        List<String> messages = new ArrayList<>();
        int messageChars = 0, hearts = 0, evaluated = 0;
        boolean cancelled = false;
        for (var handler : feature.program().eventHandlers()) {
            if (!handler.kind().equals(kind)) continue;
            authorized();
            var result = handler.evaluate(snapshot);
            evaluated++;
            for (var message : result.messages()) {
                messageChars += message.text().length();
                if (messages.size() >= 32 || messageChars > 6144) throw new IllegalArgumentException("EVENT_PREVIEW_OUTPUT_LIMIT");
                messages.add(message.text());
            }
            if (hearts == 0) hearts = result.blockHearts();
            if (result.cancelled()) { cancelled = true; break; }
        }
        authorized();
        return Map.of("simulated", true, "kind", kind, "sourceHash", feature.sourceHash(), "messages", messages,
            "cancelled", cancelled, "hearts", cancelled ? 0 : hearts, "handlersEvaluated", evaluated,
            "permissionModel", "Assumes the supplied snapshot reached each handler; no Bukkit permission, bypass, or world state is evaluated.");
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
