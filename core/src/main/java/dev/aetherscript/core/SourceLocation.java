package dev.aetherscript.core;

import java.util.Objects;

/** A source position retained through host suspension and failures. */
public record SourceLocation(String path, int line, int column) {
    public static final SourceLocation UNKNOWN = new SourceLocation("$", 0, 0);
    public SourceLocation {
        Objects.requireNonNull(path);
        if (line < 0 || column < 0) throw new IllegalArgumentException("Negative source position");
    }
    public ValidationException error(String reason) { return new ValidationException(path, reason, line, column); }
    public ValidationException locate(ValidationException failure) { return failure.located(line, column); }
}
