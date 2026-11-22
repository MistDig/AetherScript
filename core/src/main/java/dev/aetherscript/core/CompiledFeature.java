package dev.aetherscript.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record CompiledFeature(Feature definition, String sourceHash, String irHash, @JsonIgnore Program program) {
    public CompiledFeature(Feature definition, String sourceHash, String irHash) {
        this(definition, sourceHash, irHash, null);
    }
    public record EventData(String kind, java.util.Map<String, ScriptValue> fields) {
        public EventData {
            fields = java.util.Map.copyOf(fields);
            java.util.Map<String, ScriptType> expected = eventFields(kind);
            if (!fields.keySet().equals(expected.keySet())) throw new IllegalArgumentException("Event fields do not match " + kind);
            for (var field : expected.entrySet()) {
                if (!field.getValue().equals(fields.get(field.getKey()).type())) throw new IllegalArgumentException("Wrong event field type: " + field.getKey());
            }
        }
    }
    public record EventResult(List<Feature.MessageAction> messages, boolean cancelled, int blockHearts) {
        public EventResult(List<Feature.MessageAction> messages, boolean cancelled) { this(messages, cancelled, 0); }
        public EventResult { messages = List.copyOf(messages); if (blockHearts < 0 || blockHearts > 100) throw new IllegalArgumentException("block hearts must be 1..100 or absent"); }
    }
    public interface EventHandler {
        String kind();
        String permission();
        String bypassPermission();
        EventResult evaluate(EventData data);
    }
    public static final java.util.Set<String> EVENT_CAPABILITIES = java.util.Set.of("runtime.event.join", "runtime.event.quit", "runtime.event.block_break", "runtime.event.block_place", "runtime.event.cancel", "runtime.event.block_hearts", "runtime.event.entity_damage", "runtime.event.entity_death", "runtime.event.entity_target");
    public static boolean cancellableEvent(String kind) { return java.util.Set.of("block_break", "block_place", "entity_damage", "entity_target").contains(kind); }
    public static java.util.Map<String, ScriptType> eventFields(String kind) {
        if (!java.util.Set.of("join", "quit", "block_break", "block_place", "entity_damage", "entity_death", "entity_target").contains(kind)) throw new IllegalArgumentException("Unsupported event: " + kind);
        var fields = new java.util.LinkedHashMap<String, ScriptType>();
        fields.put("player_name", ScriptType.TEXT); fields.put("player_uuid", ScriptType.TEXT); fields.put("world", ScriptType.TEXT);
        if (kind.startsWith("block_")) {
            fields.put("block_type", ScriptType.TEXT);
            fields.put("x", ScriptType.INTEGER); fields.put("y", ScriptType.INTEGER); fields.put("z", ScriptType.INTEGER);
        }
        if (kind.startsWith("entity_")) {
            fields.put("entity_uuid", ScriptType.TEXT); fields.put("entity_type", ScriptType.TEXT);
            fields.put("x", ScriptType.INTEGER); fields.put("y", ScriptType.INTEGER); fields.put("z", ScriptType.INTEGER);
            switch (kind) {
                case "entity_damage" -> { fields.put("cause", ScriptType.TEXT); fields.put("damage_milli", ScriptType.INTEGER); fields.put("attacker_uuid", ScriptType.TEXT); fields.put("attacker_type", ScriptType.TEXT); }
                case "entity_death" -> { fields.put("killer_uuid", ScriptType.TEXT); fields.put("killer_type", ScriptType.TEXT); }
                case "entity_target" -> { fields.put("target_uuid", ScriptType.TEXT); fields.put("target_type", ScriptType.TEXT); fields.put("reason", ScriptType.TEXT); }
                default -> throw new IllegalArgumentException("Unsupported entity event");
            }
        }
        return java.util.Collections.unmodifiableMap(fields);
    }
    public interface Program {
        default List<EventHandler> eventHandlers() { return List.of(); }
        default boolean eventOnly() { return false; }
        List<Feature.MessageAction> evaluate(List<String> arguments);
        /** Host-aware invocation; legacy programs retain their buffered message behavior. */
        default List<Feature.MessageAction> evaluate(List<String> arguments, InvocationContext context) {
            return evaluate(arguments);
        }
    }
}
