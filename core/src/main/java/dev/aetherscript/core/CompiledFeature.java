package dev.aetherscript.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Set;

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
    public record EventResult(List<Feature.MessageAction> messages, boolean cancelled, int blockHearts,
                              ScriptValue.ItemDescriptor replacementDrop, boolean runOwnCommand) {
        public EventResult(List<Feature.MessageAction> messages, boolean cancelled) { this(messages, cancelled, 0, null, false); }
        public EventResult(List<Feature.MessageAction> messages, boolean cancelled, int blockHearts) {
            this(messages, cancelled, blockHearts, null, false);
        }
        public EventResult { messages = List.copyOf(messages); if (blockHearts < 0 || blockHearts > 100) throw new IllegalArgumentException("block hearts must be 1..100 or absent"); }
    }
    public interface EventHandler {
        String kind();
        String permission();
        String bypassPermission();
        EventResult evaluate(EventData data);
    }
    private static final Set<String> EVENT_KINDS = Set.of(
        "join", "quit", "block_break", "block_place", "block_drop", "block_interact", "item_interact", "entity_interact",
        "entity_damage", "entity_death", "entity_target", "player_respawn", "player_teleport", "item_consume", "projectile_hit",
        "player_command");
    private static final Set<String> CANCELLABLE_EVENTS = Set.of(
        "block_break", "block_place", "block_drop", "block_interact", "item_interact", "entity_interact", "entity_damage",
        "entity_target", "player_teleport", "item_consume", "projectile_hit", "player_command");
    private static final Set<String> COMMAND_TRIGGERS = Set.of("block_interact", "item_interact", "entity_interact");
    public static final Set<String> EVENT_CAPABILITIES = java.util.stream.Stream.concat(
        EVENT_KINDS.stream(), java.util.stream.Stream.of("cancel", "block_hearts", "replace_drop", "run_self"))
        .map(kind -> "runtime.event." + kind).collect(java.util.stream.Collectors.toUnmodifiableSet());
    public static boolean cancellableEvent(String kind) { return CANCELLABLE_EVENTS.contains(kind); }
    public static boolean eventCanRunCommand(String kind) { return COMMAND_TRIGGERS.contains(kind); }
    public static java.util.Map<String, ScriptType> eventFields(String kind) {
        if (!EVENT_KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported event: " + kind);
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
                case "entity_interact" -> { }
                default -> throw new IllegalArgumentException("Unsupported entity event");
            }
        }
        if (kind.equals("item_interact") || kind.equals("item_consume")) {
            fields.put("item_type", ScriptType.TEXT);
            fields.put("item_id", ScriptType.TEXT);
        }
        if (kind.equals("player_respawn") || kind.equals("player_teleport") || kind.equals("projectile_hit")) {
            fields.put("x", ScriptType.INTEGER); fields.put("y", ScriptType.INTEGER); fields.put("z", ScriptType.INTEGER);
        }
        if (kind.equals("player_teleport")) {
            fields.put("from_world", ScriptType.TEXT);
            fields.put("cause", ScriptType.TEXT);
        }
        if (kind.equals("projectile_hit")) {
            fields.put("projectile_type", ScriptType.TEXT);
            fields.put("hit_block_type", ScriptType.TEXT);
            fields.put("hit_entity_type", ScriptType.TEXT);
        }
        if (kind.equals("player_command")) fields.put("command_name", ScriptType.TEXT);
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
