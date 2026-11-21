package dev.aetherscript.core;

import java.util.Objects;

/** Immutable closed language type; map values use element and map keys use key. */
public record ScriptType(String name, ScriptType element, ScriptType key, java.util.Map<String, ScriptType> fields) {
    public enum Kind { INTEGER, TEXT, BOOLEAN, LIST, UUID, LOCATION, ITEM, WORLD, BLOCK, UNIT, OPTIONAL, MAP, SET, RECORD }
    public static final ScriptType INTEGER = new ScriptType("INTEGER", null), TEXT = new ScriptType("TEXT", null), BOOLEAN = new ScriptType("BOOLEAN", null), UUID = new ScriptType("UUID", null), LOCATION = new ScriptType("LOCATION", null), ITEM = new ScriptType("ITEM", null), WORLD = new ScriptType("WORLD", null), BLOCK = new ScriptType("BLOCK", null), UNIT = new ScriptType("UNIT", null);
    public ScriptType(String name, ScriptType element) { this(name, element, null, java.util.Map.of()); }
    public ScriptType(String name, ScriptType element, ScriptType key) { this(name, element, key, java.util.Map.of()); }
    public ScriptType {
        Objects.requireNonNull(name, "name");
        fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(Objects.requireNonNull(fields)));
        if (!fields.isEmpty()) {
            if (element != null || key != null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || fields.size() > 16) throw new IllegalArgumentException("Invalid record type");
            for (var field : fields.entrySet()) {
                if (!field.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || field.getValue() == null) throw new IllegalArgumentException("Invalid record field");
                if (1 + depth(field.getValue()) > 8) throw new IllegalArgumentException("Type nesting exceeds 8");
            }
        } else if (element == null) {
            Kind kind = Kind.valueOf(name);
            if (key != null || kind == Kind.LIST || kind == Kind.OPTIONAL || kind == Kind.MAP || kind == Kind.SET || kind == Kind.RECORD) throw new IllegalArgumentException("Invalid type shape");
        } else {
            boolean map = key != null && name.equals("MAP<" + key.name + "," + element.name + ">");
            boolean set = key == null && name.equals("SET<" + element.name + ">");
            boolean sequence = key == null && (name.equals("LIST<" + element.name + ">") || name.equals("OPTIONAL<" + element.name + ">"));
            if (!map && !set && !sequence) throw new IllegalArgumentException("Invalid container type");
            if (map && !key.isHashable() || set && !element.isHashable()) throw new IllegalArgumentException("Map keys and set elements must be hashable scalars");
            if (1 + Math.max(depth(element), key == null ? 0 : depth(key)) > 8) throw new IllegalArgumentException("Collection nesting exceeds 8");
        }
    }
    private static int depth(ScriptType type) { if (type.isRecord()) return 1 + type.fields.values().stream().mapToInt(ScriptType::depth).max().orElse(0); return type.element == null ? 0 : 1 + Math.max(depth(type.element), type.key == null ? 0 : depth(type.key)); }
    public Kind kind() {
        if (isRecord()) return Kind.RECORD;
        if (element == null) return Kind.valueOf(name);
        if (key != null) return Kind.MAP;
        if (name.startsWith("LIST<")) return Kind.LIST;
        if (name.startsWith("SET<")) return Kind.SET;
        return Kind.OPTIONAL;
    }
    public boolean isRecord() { return !fields.isEmpty(); }
    public static ScriptType record(String name, java.util.Map<String, ScriptType> fields) { if (fields.isEmpty()) throw new IllegalArgumentException("Records require 1..16 fields"); return new ScriptType(name, null, null, fields); }
    public boolean isList() { return kind() == Kind.LIST; }
    public boolean isMap() { return kind() == Kind.MAP; }
    public boolean isSet() { return kind() == Kind.SET; }
    public boolean isHashable() { return switch (kind()) { case INTEGER, TEXT, BOOLEAN, UUID, LOCATION -> true; default -> false; }; }
    public static ScriptType list(ScriptType element) { return new ScriptType("LIST<" + Objects.requireNonNull(element).name + ">", element); }
    public static ScriptType optional(ScriptType element) { return new ScriptType("OPTIONAL<" + Objects.requireNonNull(element).name + ">", element); }
    public static ScriptType set(ScriptType element) { return new ScriptType("SET<" + Objects.requireNonNull(element).name + ">", element); }
    public static ScriptType map(ScriptType key, ScriptType value) { return new ScriptType("MAP<" + Objects.requireNonNull(key).name + "," + Objects.requireNonNull(value).name + ">", value, key); }
}
