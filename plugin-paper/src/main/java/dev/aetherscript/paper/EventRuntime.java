package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.*;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
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
    private final Map<ScriptValue.BlockRef, Hearts> hearts = new HashMap<>();
    private record Hearts(String material, int maximum, int remaining, TextDisplay display) {}
    private record Subscriber(CompiledFeature feature, CompiledFeature.EventHandler handler) { }
    private List<Subscriber> subscribers(String kind) {
        Registry.Snapshot current = registry.snapshot();
        if (current != indexed) {
            clearHearts();
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!interested("entity_damage")) return;
        Entity attacker = event instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) attacker = shooter;
        Player actor = event.getEntity() instanceof Player victim ? victim : attacker instanceof Player player ? player : null;
        if (actor == null) return;
        Map<String, ScriptValue> fields = entityFields(actor, event.getEntity());
        fields.put("cause", text(event.getCause().name()));
        fields.put("damage_milli", integer(Math.max(0, Math.min(Long.MAX_VALUE, Math.round(event.getDamage() * 1000)))));
        identity(fields, "attacker", attacker);
        dispatch("entity_damage", actor, fields, event);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player actor) || !interested("entity_target")) return;
        Map<String, ScriptValue> fields = entityFields(actor, event.getEntity());
        identity(fields, "target", event.getTarget()); fields.put("reason", text(event.getReason().name()));
        dispatch("entity_target", actor, fields, event);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        if (!interested("entity_death")) return;
        Player killer = event.getEntity().getKiller();
        Player actor = event.getEntity() instanceof Player victim ? victim : killer;
        if (actor == null) return;
        Map<String, ScriptValue> fields = entityFields(actor, event.getEntity());
        identity(fields, "killer", killer);
        dispatch("entity_death", actor, fields, null);
    }
    private static Map<String, ScriptValue> entityFields(Player actor, Entity entity) {
        Map<String, ScriptValue> fields = fields(actor);
        fields.put("world", text(entity.getWorld().getKey().toString()));
        identity(fields, "entity", entity);
        var location = entity.getLocation();
        fields.put("x", integer(location.getBlockX())); fields.put("y", integer(location.getBlockY())); fields.put("z", integer(location.getBlockZ()));
        return fields;
    }
    private static void identity(Map<String, ScriptValue> fields, String prefix, Entity entity) {
        fields.put(prefix + "_uuid", text(entity == null ? "" : entity.getUniqueId().toString()));
        fields.put(prefix + "_type", text(entity == null ? "" : entity.getType().name()));
    }
    private void block(String kind, Player player, Block block, Cancellable event) {
        if (event.isCancelled() || !interested(kind)) return;
        Map<String, ScriptValue> fields = fields(player);
        fields.put("world", text(block.getWorld().getKey().toString()));
        fields.put("block_type", text(block.getType().name()));
        fields.put("x", integer(block.getX())); fields.put("y", integer(block.getY())); fields.put("z", integer(block.getZ()));
        dispatch(kind, player, fields, event, block);
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
        dispatch(kind, player, fields, event, null);
    }
    private void dispatch(String kind, Player player, Map<String, ScriptValue> fields, Cancellable event, Block block) {
        if (closed || !ready.getAsBoolean() || event != null && event.isCancelled()) return;
        List<Subscriber> handlers = subscribers(kind);
        if (handlers.isEmpty()) return;
        Registry.Snapshot captured = indexed;
        CompiledFeature.EventData data = new CompiledFeature.EventData(kind, fields);
        int maximumHearts = 0;
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
                if (maximumHearts == 0) maximumHearts = result.blockHearts();
                for (Feature.MessageAction message : result.messages()) player.sendMessage(Component.text(message.text()));
            } catch (RuntimeException failure) {
                String key = feature.definition().id() + ":" + kind;
                if (reported.add(key)) failures.accept("Event handler failed for " + feature.definition().id() + " (" + kind + "): "
                    + FeatureManager.display(failure instanceof ValidationException ? failure.getMessage() : "RUNTIME_FAILED")
                    + "; further failures suppressed until registry replacement");
            }
        }
        if (block != null && event instanceof BlockBreakEvent && !event.isCancelled() && maximumHearts > 0)
            hit(block, event, maximumHearts);
    }

    private static ScriptValue.BlockRef key(Block block) {
        return new ScriptValue.BlockRef(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
    private void hit(Block block, Cancellable event, int maximum) {
        var key = key(block);
        var previous = hearts.get(key);
        String material = block.getType().name();
        if (previous != null && (!previous.material().equals(material) || previous.maximum() != maximum)) {
            remove(key);
            previous = null;
        }
        int remaining = (previous == null ? maximum : previous.remaining()) - 1;
        if (remaining < 1) return;
        event.setCancelled(true);
        if (previous == null && hearts.size() >= 256) {
            return;
        }
        TextDisplay display = previous == null ? block.getWorld().spawn(block.getLocation().add(0.5, 1.2, 0.5), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        }) : previous.display();
        display.text(Component.text(remaining + " / " + maximum + " hearts"));
        hearts.put(key, new Hearts(material, maximum, remaining, display));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void completed(BlockBreakEvent event) { if (!hearts.isEmpty()) remove(key(event.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void replaced(BlockPlaceEvent event) { if (!hearts.isEmpty()) remove(key(event.getBlockPlaced())); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void unload(ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        hearts.keySet().stream().filter(key -> key.worldId().equals(chunk.getWorld().getUID())
            && key.x() >> 4 == chunk.getX() && key.z() >> 4 == chunk.getZ()).toList().forEach(this::remove);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(WorldUnloadEvent event) {
        hearts.keySet().stream().filter(key -> key.worldId().equals(event.getWorld().getUID())).toList().forEach(this::remove);
    }
    private void remove(ScriptValue.BlockRef key) {
        Hearts removed = hearts.remove(key);
        if (removed != null) removed.display().remove();
    }
    private void clearHearts() {
        hearts.values().forEach(health -> health.display().remove());
        hearts.clear();
    }
    void refresh() { clearHearts(); subscribers("block_break"); }
    @Override public void close() { closed = true; clearHearts(); HandlerList.unregisterAll(this); }
}
