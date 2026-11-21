package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.function.*;

/** One listener for the runtime lifetime; each delivery captures the current registry. */
final class EventRuntime implements Listener, AutoCloseable {
    private final Registry registry;
    private final Supplier<ScriptCapabilityPolicy> policy;
    private final BooleanSupplier ready;
    private final Consumer<String> failures;
    private boolean closed;
    private Registry.Snapshot indexed;
    private Map<String, List<Subscriber>> subscribers = Map.of();
    private final Set<String> reported = new HashSet<>();
    private record Subscriber(CompiledFeature feature, CompiledFeature.EventHandler handler) { }
    private List<Subscriber> subscribers(String kind) {
        Registry.Snapshot current = registry.snapshot();
        if (current != indexed) {
            Map<String, List<Subscriber>> next = new HashMap<>();
            current.features().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                CompiledFeature feature = entry.getValue();
                if (feature.program() != null) for (CompiledFeature.EventHandler handler : feature.program().eventHandlers())
                    next.computeIfAbsent(handler.kind(), ignored -> new ArrayList<>()).add(new Subscriber(feature, handler));
            });
            next.replaceAll((key, value) -> List.copyOf(value));
            subscribers = Map.copyOf(next); indexed = current; reported.clear();
        }
        return subscribers.getOrDefault(kind, List.of());
    }
    private boolean interested(String kind) { return !closed && ready.getAsBoolean() && !subscribers(kind).isEmpty(); }
    EventRuntime(Registry registry, Supplier<ScriptCapabilityPolicy> policy, BooleanSupplier ready, Consumer<String> failures) {
        this.registry = registry; this.policy = policy; this.ready = ready; this.failures = failures;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockBreak(BlockBreakEvent event) { block("block_break", event.getPlayer(), event.getBlock(), event); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockPlace(BlockPlaceEvent event) { block("block_place", event.getPlayer(), event.getBlockPlaced(), event); }
    @EventHandler public void join(PlayerJoinEvent event) { player("join", event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { player("quit", event.getPlayer()); }
    private void block(String kind, Player player, Block block, Cancellable event) {
        if (event.isCancelled() || !interested(kind)) return;
        Map<String, ScriptValue> fields = fields(player);
        fields.put("world", text(block.getWorld().getKey().toString()));
        fields.put("block_type", text(block.getType().name()));
        fields.put("x", integer(block.getX())); fields.put("y", integer(block.getY())); fields.put("z", integer(block.getZ()));
        dispatch(kind, player, fields, event);
    }
    private void player(String kind, Player player) { if (interested(kind)) dispatch(kind, player, fields(player), null); }
    private static Map<String, ScriptValue> fields(Player player) {
        Map<String, ScriptValue> fields = new HashMap<>();
        fields.put("player_name", text(player.getName())); fields.put("player_uuid", text(player.getUniqueId().toString()));
        fields.put("world", text(player.getWorld().getKey().toString()));
        return fields;
    }
    private static ScriptValue text(String text) { return new ScriptValue(ScriptType.TEXT, text); }
    private static ScriptValue integer(long number) { return new ScriptValue(ScriptType.INTEGER, number); }
    void dispatch(String kind, Player player, Map<String, ScriptValue> fields, Cancellable event) {
        if (closed || !ready.getAsBoolean() || event != null && event.isCancelled()) return;
        List<Subscriber> handlers = subscribers(kind);
        if (handlers.isEmpty()) return;
        Registry.Snapshot captured = indexed;
        CompiledFeature.EventData data = new CompiledFeature.EventData(kind, fields);
        for (Subscriber subscriber : handlers) {
            CompiledFeature feature = subscriber.feature();
            CompiledFeature.EventHandler handler = subscriber.handler();
            if (event != null && event.isCancelled()) return;
            if (!handler.permission().equals("*") && !player.hasPermission(handler.permission())
                || handler.bypassPermission() != null && player.hasPermission(handler.bypassPermission())) continue;
            // Owner policy denial is expected after restrict; execution errors are reported separately.
            try { policy.get().validate(feature); }
            catch (ValidationException | SecurityException denied) { continue; }
            if (captured != registry.snapshot() || closed || !ready.getAsBoolean()) return;
            try {
                CompiledFeature.EventResult result = handler.evaluate(data);
                if (result.cancelled() && event != null) event.setCancelled(true);
                for (Feature.MessageAction message : result.messages()) player.sendMessage(Component.text(message.text()));
            } catch (RuntimeException failure) {
                String key = feature.definition().id() + ":" + kind;
                if (reported.add(key)) failures.accept("Event handler failed for " + feature.definition().id() + " (" + kind + "): "
                    + FeatureManager.display(failure instanceof ValidationException ? failure.getMessage() : "RUNTIME_FAILED")
                    + "; further failures suppressed until registry replacement");
            }
        }
    }

    @Override public void close() { closed = true; HandlerList.unregisterAll(this); }
}
