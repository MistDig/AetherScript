package dev.aetherscript.core;

import java.util.*;

/** Typed allowlist. The host alone implements platform operations and scheduling. */
public final class CallableRegistry {
    public enum ExecutionContext { PURE, PAPER_MAIN, ASYNC_IO }
    public record Descriptor(String id, String dslName, List<ScriptType> parameters, ScriptType result,
                             ExecutionContext context, Set<String> requiredCapabilities, boolean playerRequired,
                             String platform, String errorContract, String documentation, String owner) {
        public Descriptor {
            Objects.requireNonNull(id); Objects.requireNonNull(dslName);
            if (!id.matches("[a-z][a-z0-9_.-]{0,127}") || !dslName.matches("[a-z][a-z0-9_]{0,63}"))
                throw new IllegalArgumentException("Invalid callable ID or DSL name");
            parameters = List.copyOf(parameters); Objects.requireNonNull(result); Objects.requireNonNull(context);
            requiredCapabilities = Set.copyOf(requiredCapabilities);
            Objects.requireNonNull(platform); Objects.requireNonNull(errorContract); Objects.requireNonNull(documentation); Objects.requireNonNull(owner);
            if (platform.isBlank() || errorContract.isBlank() || documentation.isBlank() || owner.isBlank())
                throw new IllegalArgumentException("Callable metadata must be nonblank");
        }
    }
    private final Map<String, Descriptor> names = new LinkedHashMap<>();
    private final Set<String> ids = new HashSet<>();
    public synchronized void register(Descriptor descriptor) {
        Objects.requireNonNull(descriptor);
        if (names.containsKey(descriptor.dslName()) || ids.contains(descriptor.id())) throw new IllegalArgumentException("Duplicate callable ID or DSL name");
        names.put(descriptor.dslName(), descriptor); ids.add(descriptor.id());
    }
    public synchronized Descriptor descriptor(String name) { return names.get(name); }
    public synchronized List<Descriptor> descriptors() { return List.copyOf(names.values()); }
    public ScriptValue invoke(String name, List<ScriptValue> arguments, InvocationContext context, SourceLocation at) {
        Objects.requireNonNull(at);
        Descriptor descriptor = descriptor(name);
        if (descriptor == null) throw at.error("Unknown callable: " + name);
        if (context == null) throw at.error("Callable requires a host invocation context");
        List<ScriptValue> args;
        try { args = List.copyOf(arguments); } catch (RuntimeException invalid) { throw at.error("Invalid callable arguments"); }
        if (args.size() != descriptor.parameters().size()) throw at.error("Wrong argument count for " + name);
        for (int i = 0; i < args.size(); i++)
            if (!descriptor.parameters().get(i).equals(args.get(i).type())) throw at.error("Wrong argument type for " + name + " at argument " + (i + 1));
        context.require(descriptor.requiredCapabilities(), descriptor.playerRequired(), at);
        try (ExecutionBudget.Scope ignored = context.budget().enter(at)) {
            // The host dispatches to its executor and may park only the invoking virtual thread.
            if (descriptor.context() != ExecutionContext.PURE && !Thread.currentThread().isVirtual())
                throw at.error("Host callable requires an invocation virtual thread");
            ScriptValue result = context.invoke(descriptor, args);
            context.require(descriptor.requiredCapabilities(), descriptor.playerRequired(), at);
            if (result == null || !descriptor.result().equals(result.type())) throw at.error("Host returned wrong type for " + name);
            return result;
        } catch (ValidationException failure) { throw at.locate(failure); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw at.error("Invocation cancelled"); }
        catch (Exception failure) { throw at.error("Callable " + name + " failed: " + descriptor.errorContract()); }
    }
}

