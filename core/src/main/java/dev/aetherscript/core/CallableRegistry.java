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
    private boolean frozen;
    public synchronized void register(Descriptor descriptor) {
        Objects.requireNonNull(descriptor);
        if (frozen) throw new IllegalStateException("Callable catalog is a frozen compiler snapshot");
        if (names.containsKey(descriptor.dslName()) || ids.contains(descriptor.id())) throw new IllegalArgumentException("Duplicate callable ID or DSL name");
        names.put(descriptor.dslName(), descriptor); ids.add(descriptor.id());
    }
    public synchronized Descriptor descriptor(String name) { return names.get(name); }
    public synchronized List<Descriptor> descriptors() { return List.copyOf(names.values()); }
    /** Bind a compiler to a stable catalogue without preventing provider setup in its source registry. */
    public synchronized CallableRegistry snapshot() {
        if (frozen) return this;
        CallableRegistry copy = new CallableRegistry();
        names.values().forEach(copy::register); copy.frozen = true; return copy;
    }
    public static boolean typedState(Descriptor descriptor) {
        return descriptor.id().equals("state.own.save") || descriptor.id().equals("state.own.load") || descriptor.id().equals("state.shared.read") || descriptor.id().equals("state.shared.commit");
    }
    /** Zero-based argument which determines the polymorphic state signature. */
    public static int stateTypeArgument(Descriptor descriptor) {
        return descriptor.id().equals("state.shared.read") ? 2 : 1;
    }
    public static Descriptor specialize(Descriptor descriptor, ScriptType type) {
        if (!typedState(descriptor)) return descriptor;
        if (descriptor.id().equals("state.shared.commit") && (!type.isMap() || !type.key().equals(ScriptType.TEXT)))
            throw new IllegalArgumentException("STATE_TYPE_UNSUPPORTED: shared writes must be a map with text keys");
        if (containsUnit(type)) throw new IllegalArgumentException("STATE_TYPE_UNSUPPORTED: unit cannot be persisted");
        if (descriptor.id().equals("state.shared.read"))
            return new Descriptor(descriptor.id(), descriptor.dslName(), List.of(ScriptType.TEXT, ScriptType.TEXT, type), type,
                descriptor.context(), descriptor.requiredCapabilities(), descriptor.playerRequired(), descriptor.platform(), descriptor.errorContract(), descriptor.documentation(), descriptor.owner());
        if (descriptor.id().equals("state.shared.commit"))
            return new Descriptor(descriptor.id(), descriptor.dslName(), List.of(ScriptType.TEXT, type, ScriptType.list(ScriptType.TEXT)), ScriptType.BOOLEAN,
                descriptor.context(), descriptor.requiredCapabilities(), descriptor.playerRequired(), descriptor.platform(), descriptor.errorContract(), descriptor.documentation(), descriptor.owner());
        return new Descriptor(descriptor.id(), descriptor.dslName(), List.of(ScriptType.TEXT, type),
            descriptor.id().equals("state.own.load") ? type : ScriptType.UNIT, descriptor.context(), descriptor.requiredCapabilities(),
            descriptor.playerRequired(), descriptor.platform(), descriptor.errorContract(), descriptor.documentation(), descriptor.owner());
    }
    private static boolean containsUnit(ScriptType type) {
        return type.fields().values().stream().anyMatch(CallableRegistry::containsUnit) || type.equals(ScriptType.UNIT) || type.element() != null && containsUnit(type.element()) || type.key() != null && containsUnit(type.key());
    }
    public ScriptValue invoke(String name, List<ScriptValue> arguments, InvocationContext context, SourceLocation at) {
        Objects.requireNonNull(at);
        Descriptor descriptor = descriptor(name);
        if (descriptor == null) throw at.error("Unknown callable: " + name);
        if (context == null) throw at.error("Callable requires a host invocation context");
        List<ScriptValue> args;
        try { args = List.copyOf(arguments); } catch (RuntimeException invalid) { throw at.error("Invalid callable arguments"); }
        if (typedState(descriptor) && args.size() == descriptor.parameters().size()) {
            try { descriptor = specialize(descriptor, args.get(stateTypeArgument(descriptor)).type()); }
            catch (IllegalArgumentException invalid) { throw at.error(invalid.getMessage()); }
        }
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

