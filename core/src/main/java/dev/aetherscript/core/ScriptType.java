package dev.aetherscript.core;

import java.util.Objects;

/** Immutable closed language type; element is present only for list and optional. */
public record ScriptType(String name, ScriptType element) {
    public enum Kind { INTEGER, TEXT, BOOLEAN, LIST, UUID, LOCATION, UNIT, OPTIONAL }
    public static final ScriptType INTEGER = new ScriptType("INTEGER", null), TEXT = new ScriptType("TEXT", null), BOOLEAN = new ScriptType("BOOLEAN", null), UUID = new ScriptType("UUID", null), LOCATION = new ScriptType("LOCATION", null), UNIT = new ScriptType("UNIT", null);
    public ScriptType {
        Objects.requireNonNull(name, "name");
        if (element == null) {
            Kind kind = Kind.valueOf(name);
            if (kind == Kind.LIST || kind == Kind.OPTIONAL) throw new IllegalArgumentException("Container type requires element");
        } else {
            if (!name.equals("LIST<" + element.name + ">") && !name.equals("OPTIONAL<" + element.name + ">")) throw new IllegalArgumentException("Invalid container type");
            int depth = 1;
            for (ScriptType type = element; type.element != null; type = type.element) depth++;
            if (depth > 8) throw new IllegalArgumentException("Collection nesting exceeds 8");
        }
    }
    public Kind kind() { return element == null ? Kind.valueOf(name) : name.startsWith("LIST<") ? Kind.LIST : Kind.OPTIONAL; }
    public boolean isList() { return kind() == Kind.LIST; }
    public static ScriptType list(ScriptType element) { return new ScriptType("LIST<" + Objects.requireNonNull(element).name + ">", element); }
    public static ScriptType optional(ScriptType element) { return new ScriptType("OPTIONAL<" + Objects.requireNonNull(element).name + ">", element); }
}
