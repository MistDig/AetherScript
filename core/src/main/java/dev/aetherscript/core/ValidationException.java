package dev.aetherscript.core;
public final class ValidationException extends RuntimeException {
    private final String path;
    public ValidationException(String path, String message) { super("VALIDATION_FAILED " + path + ": " + message); this.path = path; }
    public String path() { return path; }
}
