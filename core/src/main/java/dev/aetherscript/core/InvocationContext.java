package dev.aetherscript.core;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Authority captured by the host for exactly one revision and actor. */
public final class InvocationContext {
    @FunctionalInterface public interface HostInvoker {
        ScriptValue invoke(CallableRegistry.Descriptor descriptor, List<ScriptValue> arguments, InvocationContext context) throws Exception;
    }
    private final String featureId, revisionToken;
    private final UUID actor;
    private final Set<String> capabilities;
    private final ExecutionBudget budget;
    private final BooleanSupplier currentOwner;
    private final HostInvoker host;
    private final Consumer<String> messageSender;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public InvocationContext(String featureId, String revisionToken, UUID actor, Set<String> capabilities,
                             ExecutionBudget budget, BooleanSupplier currentOwner, HostInvoker host, Consumer<String> messageSender) {
        FeatureParser.requireId(featureId);
        this.featureId = featureId; this.revisionToken = Objects.requireNonNull(revisionToken);
        this.actor = actor; this.capabilities = Set.copyOf(capabilities); this.budget = Objects.requireNonNull(budget);
        this.currentOwner = Objects.requireNonNull(currentOwner); this.host = Objects.requireNonNull(host);
        this.messageSender = Objects.requireNonNull(messageSender);
    }
    public String featureId() { return featureId; }
    public String revisionToken() { return revisionToken; }
    public UUID actor() { return actor; }
    public Set<String> capabilities() { return capabilities; }
    public ExecutionBudget budget() { return budget; }
    public void cancel() { cancelled.set(true); }
    public boolean cancelled() { return cancelled.get(); }
    public void checkActive(SourceLocation at) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw at.error("Invocation cancelled");
        if (!currentOwner.getAsBoolean()) throw at.error("Invocation revision or owner is stale");
        budget.check(at);
    }
    public void require(Set<String> required, boolean playerRequired, SourceLocation at) {
        checkActive(at);
        if (!capabilities.containsAll(required)) throw at.error("Missing required capability: " + required);
        if (playerRequired && actor == null) throw at.error("Callable requires a player actor");
    }
    ScriptValue invoke(CallableRegistry.Descriptor descriptor, List<ScriptValue> arguments) throws Exception { return host.invoke(descriptor, arguments, this); }
    public void send(String text, SourceLocation at) {
        require(Set.of("runtime.message.send"), false, at);
        if (!Thread.currentThread().isVirtual()) throw at.error("Message delivery requires an invocation virtual thread");
        Objects.requireNonNull(text); budget.step(at); budget.message(at);
        try { messageSender.accept(text); }
        catch (ValidationException failure) { throw at.locate(failure); }
        catch (RuntimeException failure) { throw at.error("Message delivery failed"); }
        checkActive(at);
    }
}



