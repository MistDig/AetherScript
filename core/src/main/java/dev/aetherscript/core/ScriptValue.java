package dev.aetherscript.core;

import java.util.List;
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
    public ScriptValue {
        Objects.requireNonNull(type, "type"); Objects.requireNonNull(value, "value");
        if (type.isList() && value instanceof List<?> list) value = List.copyOf(list);
        boolean valid = switch (type.kind()) {
            case INTEGER -> value instanceof Long;
            case TEXT -> value instanceof String text && text.length() <= FeatureParser.MAX_TEXT;
            case BOOLEAN -> value instanceof Boolean;
            case UUID -> value instanceof UUID;
            case LOCATION -> value instanceof LocationSnapshot;
            case UNIT -> value == Unit.INSTANCE;
            case LIST -> value instanceof List<?> list && list.size() <= 256 && list.stream().allMatch(item -> item instanceof ScriptValue child && child.type.equals(type.element()));
            case OPTIONAL -> value instanceof Optional<?> optional && (optional.isEmpty() || optional.get() instanceof ScriptValue child && child.type.equals(type.element()));
        };
        if (!valid) throw new IllegalArgumentException("Invalid value for " + type.name());
    }
    public long integer() { return (Long) value; }
    public boolean bool() { return (Boolean) value; }
    public String text() { return (String) value; }
    @SuppressWarnings("unchecked") public List<ScriptValue> list() { return (List<ScriptValue>) value; }
    public UUID uuid() { return (UUID) value; }
    public LocationSnapshot location() { return (LocationSnapshot) value; }
    @SuppressWarnings("unchecked") public Optional<ScriptValue> optional() { return (Optional<ScriptValue>) value; }
}
