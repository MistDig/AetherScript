package dev.aetherscript.paper.api;

import dev.aetherscript.core.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Retrieve through Bukkit ServicesManager. Addons are trusted Java plugins; scripts need separate grants. */
public interface AddonService {
    @FunctionalInterface interface Handler {
        ScriptValue invoke(Call call, List<ScriptValue> arguments) throws Exception;
    }
    record Call(String featureId, String revision, UUID actorId, Player player) {}
    record Callable(String name, List<ScriptType> parameters, ScriptType result,
                    boolean playerRequired, String documentation, Handler handler) {
        public Callable {
            Objects.requireNonNull(name); parameters = List.copyOf(parameters); Objects.requireNonNull(result);
            Objects.requireNonNull(documentation); Objects.requireNonNull(handler);
            if (!name.matches("[a-z][a-z0-9_]{0,31}") || documentation.isBlank() || documentation.length() > 4096 || parameters.size() > 16)
                throw new IllegalArgumentException("Invalid addon callable metadata");
        }
    }
    record Installed(String plugin, String namespace, String version, List<CallableRegistry.Descriptor> callables) {
        public Installed { callables = List.copyOf(callables); }
    }
    /** Atomic batch. Main server thread only; names and IDs cannot shadow builtins or another addon. */
    void register(Plugin owner, String namespace, String version, List<Callable> callables);
    void unregister(Plugin owner);
    List<Installed> installed();
    List<CallableRegistry.Descriptor> descriptors();
}
