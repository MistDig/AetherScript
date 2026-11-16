package dev.aetherscript.core;

/** One invocation's shared limits, retained across functions and host waits. */
public final class ExecutionBudget {
    public static final int MAX_STEPS = 8192, MAX_CALL_DEPTH = 32, MAX_MESSAGES = 32;
    private final int maxSteps, maxDepth, maxMessages;
    private int steps, depth, messages;
    public ExecutionBudget() { this(MAX_STEPS, MAX_CALL_DEPTH, MAX_MESSAGES); }
    public ExecutionBudget(int steps, int depth, int messages) {
        if (steps < 1 || depth < 1 || messages < 1 || steps > MAX_STEPS || depth > MAX_CALL_DEPTH || messages > MAX_MESSAGES)
            throw new IllegalArgumentException("Budget must be positive and within runtime limits");
        maxSteps = steps; maxDepth = depth; maxMessages = messages;
    }
    public synchronized void step(SourceLocation at) {
        if (steps >= maxSteps) throw at.error("Execution step limit exceeded");
        steps++;
    }
    public synchronized void check(SourceLocation at) {
        if (steps > maxSteps || depth > maxDepth || messages > maxMessages) throw at.error("Execution budget exceeded");
    }
    public synchronized Scope enter(SourceLocation at) {
        step(at);
        if (depth >= maxDepth) throw at.error("Maximum call depth exceeded");
        depth++;
        return new Scope();
    }
    public synchronized void message(SourceLocation at) {
        if (messages >= maxMessages) throw at.error("Maximum message count exceeded");
        messages++;
    }
    public synchronized int stepsUsed() { return steps; }
    public synchronized int messagesUsed() { return messages; }
    public final class Scope implements AutoCloseable {
        private boolean closed;
        private Scope() {}
        @Override public void close() { synchronized (ExecutionBudget.this) { if (!closed) { closed = true; depth--; } } }
    }
}
