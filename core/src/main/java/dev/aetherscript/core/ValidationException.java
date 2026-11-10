package dev.aetherscript.core;

public final class ValidationException extends RuntimeException {
    private final String path, reason;
    private final int line, column;
    public ValidationException(String path, String reason) { this(path, reason, 0, 0); }
    public ValidationException(String path, String reason, int line, int column) {
        super("VALIDATION_FAILED " + (line > 0 ? "line " + line + ", column " + column + ": " : "") + path + ": " + reason);
        this.path = path; this.reason = reason; this.line = line; this.column = column;
    }
    public String path() { return path; }
    public String reason() { return reason; }
    public int line() { return line; }
    public int column() { return column; }
    public ValidationException located(int line, int column) { return this.line > 0 ? this : new ValidationException(path, reason, line, column); }
}
