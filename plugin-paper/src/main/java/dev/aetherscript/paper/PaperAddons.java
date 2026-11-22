package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import dev.aetherscript.paper.api.AddonService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Authoritative live barrel; compilers receive immutable descriptor snapshots. */
final class PaperAddons implements AddonService, Listener, AutoCloseable {
    private record Entry(Plugin plugin, String namespace, String version, CallableRegistry.Descriptor descriptor, Handler handler) {}
    private final Server server;
    private final Runnable changed;
    private final Set<String> builtinIds = new HashSet<>(), builtinNames = new HashSet<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean closed;
    PaperAddons(Server server, Collection<CallableRegistry.Descriptor> builtins, Runnable changed) {
        this.server = Objects.requireNonNull(server); this.changed = Objects.requireNonNull(changed);
        builtins.forEach(descriptor -> { builtinIds.add(descriptor.id()); builtinNames.add(descriptor.dslName()); });
    }
    private void mainThread() {
        if (!server.isPrimaryThread()) throw new IllegalStateException("Addon registry requires the main server thread");
    }
    @Override public synchronized void register(Plugin owner, String namespace, String version, List<Callable> callables) {
        mainThread(); Objects.requireNonNull(owner); Objects.requireNonNull(namespace); Objects.requireNonNull(version);
        if (closed || !owner.isEnabled() || server.getPluginManager().getPlugin(owner.getName()) != owner) throw new IllegalStateException("Addon owner is unavailable");
        if (!namespace.matches("[a-z][a-z0-9_]{0,23}") || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")) throw new IllegalArgumentException("Invalid addon namespace/version");
        List<Callable> batch = List.copyOf(callables);
        if (batch.isEmpty() || batch.size() > 64 || entries.size() + batch.size() > 256) throw new IllegalArgumentException("Addon callable limit exceeded");
        if (entries.values().stream().anyMatch(entry -> entry.namespace().equals(namespace) || entry.plugin() == owner)) throw new IllegalArgumentException("Addon owner or namespace is already registered");
        Map<String, Entry> staged = new LinkedHashMap<>();
        Set<String> names = new HashSet<>(builtinNames);
        entries.values().forEach(entry -> names.add(entry.descriptor().dslName()));
        for (Callable callable : batch) {
            String id = "addon." + namespace + "." + callable.name();
            String name = namespace + "_" + callable.name();
            var descriptor = new CallableRegistry.Descriptor(id, name, callable.parameters(), callable.result(),
                CallableRegistry.ExecutionContext.PAPER_MAIN, Set.of(id), callable.playerRequired(), "paper", "ADDON_CALL_FAILED",
                callable.documentation(), owner.getName());
            if (builtinIds.contains(id) || entries.containsKey(id) || staged.containsKey(id) || !names.add(name)) throw new IllegalArgumentException("Duplicate addon callable");
            staged.put(id, new Entry(owner, namespace, version, descriptor, callable.handler()));
        }
        entries.putAll(staged); changed.run();
    }
    @Override public synchronized void unregister(Plugin owner) {
        mainThread(); Objects.requireNonNull(owner);
        if (entries.values().removeIf(entry -> entry.plugin() == owner)) changed.run();
    }
    @Override public synchronized List<CallableRegistry.Descriptor> descriptors() {
        return entries.values().stream().map(Entry::descriptor).toList();
    }
    @Override public synchronized List<Installed> installed() {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        entries.values().forEach(entry -> groups.computeIfAbsent(entry.namespace(), ignored -> new ArrayList<>()).add(entry));
        return groups.values().stream().map(group -> new Installed(group.getFirst().plugin().getName(), group.getFirst().namespace(),
            group.getFirst().version(), group.stream().map(Entry::descriptor).toList())).toList();
    }
    synchronized ScriptValue invoke(CallableRegistry.Descriptor descriptor, List<ScriptValue> arguments, InvocationContext context, Player actor) throws Exception {
        mainThread();
        Entry entry = entries.get(descriptor.id());
        if (closed || entry == null || entry.descriptor() != descriptor || !entry.plugin().isEnabled()
            || server.getPluginManager().getPlugin(entry.plugin().getName()) != entry.plugin()) throw error("ADDON_UNAVAILABLE_OR_CHANGED");
        SourceLocation location = new SourceLocation("$addon", 1, 1);
        context.require(descriptor.requiredCapabilities(), descriptor.playerRequired(), location);
        if (actor != null && !actor.getUniqueId().equals(context.actor())) throw error("ADDON_ACTOR_MISMATCH");
        if (descriptor.playerRequired() && actor == null) throw error("ADDON_PLAYER_REQUIRED");
        List<ScriptValue> checked = List.copyOf(arguments);
        if (checked.size() != descriptor.parameters().size()) throw error("ADDON_ARGUMENT_TYPE");
        for (int index = 0; index < checked.size(); index++) if (!checked.get(index).type().equals(descriptor.parameters().get(index))) throw error("ADDON_ARGUMENT_TYPE");
        ScriptValue result = entry.handler().invoke(new Call(context.featureId(), context.revisionToken(), context.actor(), actor), checked);
        // A handler may disable itself or change script authority reentrantly.
        if (entries.get(descriptor.id()) != entry || !entry.plugin().isEnabled()) throw error("ADDON_UNAVAILABLE_OR_CHANGED");
        context.require(descriptor.requiredCapabilities(), descriptor.playerRequired(), location);
        if (result == null || !descriptor.result().equals(result.type())) throw error("ADDON_RESULT_TYPE");
        return result;
    }
    @EventHandler(priority = EventPriority.MONITOR) public void disable(PluginDisableEvent event) { unregister(event.getPlugin()); }
    @Override public synchronized void close() {
        mainThread(); closed = true; entries.clear(); changed.run(); HandlerList.unregisterAll(this);
    }
    private static ValidationException error(String message) { return new ValidationException("$", message); }
}
