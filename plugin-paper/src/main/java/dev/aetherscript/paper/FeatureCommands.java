package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Main-thread-only ownership of the one command declared by each active feature. */
final class FeatureCommands implements AutoCloseable {
    private static final String PREFIX = "aetherscript";
    private final CommandMap commands;
    private final Registry registry;
    private final BooleanSupplier ready;
    private Map<String, OwnedCommand> owned = Map.of();

    FeatureCommands(CommandMap commands, Registry registry, BooleanSupplier ready) {
        this.commands = commands; this.registry = registry; this.ready = ready;
    }

    Registry.Snapshot activate(long expected, Map<String, CompiledFeature> target) {
        if (registry.snapshot().generation() != expected) throw new IllegalStateException("STALE_PLAN");
        Map<String, OwnedCommand> next = new LinkedHashMap<>();
        target.forEach((id, feature) -> {
            String name = feature.definition().command().name();
            if (next.putIfAbsent(name, new OwnedCommand(name, id, feature.definition().command().permission())) != null)
                throw new ValidationException("$.commands", "duplicate command name");
            for (String label : List.of(name, PREFIX + ":" + name)) {
                Command existing = commands.getKnownCommands().get(label);
                if (existing != null && !owned.containsValue(existing))
                    throw new ValidationException("$.command.name", "command already registered: " + label);
            }
        });
        // Restore exact previous bindings if either registration or registry validation fails.
        Map<String, Command> previous = new LinkedHashMap<>();
        for (var entry : commands.getKnownCommands().entrySet()) {
            if (owned.containsValue(entry.getValue())) previous.put(entry.getKey(), entry.getValue());
        }
        Map<String, OwnedCommand> old = owned;
        remove(old.values());
        try {
            for (OwnedCommand command : next.values()) {
                if (!commands.register(PREFIX, command) || commands.getCommand(command.getName()) != command)
                    throw new ValidationException("$.command.name", "command registration rejected: " + command.getName());
            }
            Registry.Snapshot activated = registry.replace(expected, target);
            owned = next;
            return activated;
        } catch (RuntimeException failure) {
            remove(next.values());
            previous.forEach((label, command) -> {
                if (commands.getKnownCommands().get(label) == null) commands.getKnownCommands().put(label, command);
            });
            old.values().forEach(command -> command.register(commands));
            throw failure;
        }
    }

    private void remove(Collection<OwnedCommand> entries) {
        // Paper's forwarding command map has a read-only entry iterator. Mutate through
        // its explicit remove method so the Brigadier tree is updated as well.
        List<String> labels = new ArrayList<>();
        for (var entry : commands.getKnownCommands().entrySet()) {
            if (entries.contains(entry.getValue())) labels.add(entry.getKey());
        }
        for (String label : labels) {
            if (entries.contains(commands.getKnownCommands().get(label))) commands.getKnownCommands().remove(label);
        }
        entries.forEach(command -> command.unregister(commands));
    }

    @Override public void close() { remove(owned.values()); owned = Map.of(); }

    private final class OwnedCommand extends Command implements PluginIdentifiableCommand {
        private final String featureId;
        OwnedCommand(String name, String featureId, String permission) {
            super(name); this.featureId = featureId; setPermission(permission);
        }
        @Override public org.bukkit.plugin.Plugin getPlugin() {
            return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(FeatureCommands.class);
        }
        @Override public boolean execute(CommandSender sender, String label, String[] arguments) {
            try {
                if (!ready.getAsBoolean() || !registry.run(featureId, InvocationArguments.fromCommandTokens(arguments),
                    sender instanceof Player, sender::hasPermission, text -> sender.sendMessage(Component.text(text))))
                    sender.sendMessage(Component.text("Feature unavailable or permission denied."));
            } catch (ValidationException failure) {
                sender.sendMessage(Component.text(FeatureManager.display(failure.getMessage())));
            }
            return true;
        }
        @Override public List<String> tabComplete(CommandSender sender, String alias, String[] args) { return List.of(); }
    }
}
