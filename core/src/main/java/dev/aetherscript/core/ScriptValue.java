package dev.aetherscript.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Strict immutable values accepted across the host boundary. */
public record ScriptValue(ScriptType type, Object value) {
    public enum Unit { INSTANCE }
    public record LocationSnapshot(UUID worldId, double x, double y, double z, float yaw, float pitch) {
        public LocationSnapshot {
            Objects.requireNonNull(worldId, "worldId");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Location coordinates must be finite");
        }
    }
    public record WorldRef(UUID worldId) { public WorldRef { Objects.requireNonNull(worldId, "worldId"); } }
    public record BlockRef(UUID worldId, int x, int y, int z) {
        public BlockRef {
            Objects.requireNonNull(worldId, "worldId");
            if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000 || Math.abs((long)y) > 20_000_000)
                throw new IllegalArgumentException("Block outside supported world coordinate bounds");
        }
    }
    /** Plain vanilla material descriptor; platform validation occurs at the inventory boundary. */
    public record ItemDescriptor(String material, int amount) {
        public ItemDescriptor {
            Objects.requireNonNull(material, "material");
            if (!material.matches("(?:minecraft:)?[a-z0-9_]{1,128}")) throw new IllegalArgumentException("Item material must be a vanilla lowercase material key");
            if (!material.startsWith("minecraft:")) material = "minecraft:" + material;
            if (amount < 1 || amount > 2304) throw new IllegalArgumentException("Item amount must be 1..2304");
        }
    }
    public ScriptValue {
        Objects.requireNonNull(type, "type"); Objects.requireNonNull(value, "value");
        if (type.isList() && value instanceof List<?> list) value = List.copyOf(list);
        if (type.isMap() && value instanceof Map<?, ?> map) {
            if (map.size() > 256) throw new IllegalArgumentException("Map exceeds 256 entries");
            value = Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }
        if (type.isSet() && value instanceof Set<?> set) {
            if (set.size() > 256) throw new IllegalArgumentException("Set exceeds 256 elements");
            value = Collections.unmodifiableSet(new LinkedHashSet<>(set));
        }
        boolean valid = switch (type.kind()) {
            case INTEGER -> value instanceof Long;
            case TEXT -> value instanceof String text && text.length() <= FeatureParser.MAX_TEXT;
            case BOOLEAN -> value instanceof Boolean;
            case UUID -> value instanceof UUID;
            case LOCATION -> value instanceof LocationSnapshot;
            case ITEM -> value instanceof ItemDescriptor;
            case WORLD -> value instanceof WorldRef;
            case BLOCK -> value instanceof BlockRef;
            case UNIT -> value == Unit.INSTANCE;
            case LIST -> value instanceof List<?> list && list.size() <= 256 && list.stream().allMatch(item -> item instanceof ScriptValue child && child.type.equals(type.element()));
            case MAP -> value instanceof Map<?, ?> map && map.size() <= 256 && map.entrySet().stream().allMatch(entry -> entry.getKey() instanceof ScriptValue k && k.type.equals(type.key()) && entry.getValue() instanceof ScriptValue v && v.type.equals(type.element()));
            case SET -> value instanceof Set<?> set && set.size() <= 256 && set.stream().allMatch(item -> item instanceof ScriptValue child && child.type.equals(type.element()));
            case OPTIONAL -> value instanceof Optional<?> optional && (optional.isEmpty() || optional.get() instanceof ScriptValue child && child.type.equals(type.element()));
        };
        if (!valid) throw new IllegalArgumentException("Invalid value for " + type.name());
    }
    public WorldRef world() { return (WorldRef) value; }
    public BlockRef block() { return (BlockRef) value; }
    public ItemDescriptor item() { return (ItemDescriptor) value; }
    public long integer() { return (Long) value; }
    public boolean bool() { return (Boolean) value; }
    public String text() { return (String) value; }
    @SuppressWarnings("unchecked") public List<ScriptValue> list() { return (List<ScriptValue>) value; }
    @SuppressWarnings("unchecked") public Map<ScriptValue, ScriptValue> map() { return (Map<ScriptValue, ScriptValue>) value; }
    @SuppressWarnings("unchecked") public Set<ScriptValue> set() { return (Set<ScriptValue>) value; }
    public UUID uuid() { return (UUID) value; }
    public LocationSnapshot location() { return (LocationSnapshot) value; }
    @SuppressWarnings("unchecked") public Optional<ScriptValue> optional() { return (Optional<ScriptValue>) value; }
}
