package dev.aetherscript.core;
import java.util.*;
import java.util.function.*;

public final class Registry {
    public record Snapshot(long generation, Map<String, CompiledFeature> features) {
        public Snapshot { features = Map.copyOf(features); }
    }
    private volatile Snapshot current = new Snapshot(0, Map.of());
    public Snapshot snapshot() { return current; }
    public synchronized Snapshot replace(long expected, Map<String, CompiledFeature> features) {
        if (current.generation() != expected) throw new IllegalStateException("STALE_PLAN");
        if (features.size() > 128) throw new ValidationException("$", "maximum 128 active features");
        Set<String> names = new HashSet<>();
        features.forEach((id, f) -> {
            if (!id.equals(f.definition().id())) throw new ValidationException("$.id", "ID mismatch");
            if (!names.add(f.definition().command().name())) throw new ValidationException("$.commands", "duplicate command name");
        });
        current = new Snapshot(Math.incrementExact(current.generation()), features);
        return current;
    }
    /** Caller must run on the platform thread; one invocation captures one immutable generation. */
    public boolean run(String id, boolean player, Predicate<String> permitted, Consumer<String> send) {
        Snapshot captured = current;
        CompiledFeature feature = captured.features().get(id);
        if (feature == null) return false;
        Feature.Command command = feature.definition().command();
        if ((!player && command.sender().equals("player")) || !permitted.test(command.permission())) return false;
        for (Feature.MessageAction action : command.actions()) {
            if (captured != current || !permitted.test(command.permission())) return false;
            send.accept(action.text());
        }
        return true;
    }
}
