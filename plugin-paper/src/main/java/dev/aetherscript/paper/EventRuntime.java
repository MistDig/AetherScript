package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.function.*;

/** One listener for the runtime lifetime; each delivery captures the current registry. */
final class EventRuntime implements Listener, AutoCloseable {
    private static final NamespacedKey CUSTOM_ITEM = Objects.requireNonNull(NamespacedKey.fromString("aetherscript:custom_item"));
    private final Registry registry;
    private final Supplier<ScriptCapabilityPolicy> policy;
    private final BooleanSupplier ready;
    private final Consumer<String> failures;
    private final BiFunction<ScriptValue.ItemDescriptor, String, ItemStack> dropStack;
    private final Consumer<Runnable> nextTick;
    private final BiConsumer<String, Player> runCommand;
    private boolean closed;
    private Registry.Snapshot indexed;
    private Map<String, List<Subscriber>> subscribers = Map.of();
    private final Set<String> reported = new HashSet<>();
    private final Map<ScriptValue.BlockRef, Hearts> hearts = new HashMap<>();
    private final Set<PendingCommand> pendingCommands = new HashSet<>();
    private record PendingCommand(String featureId, UUID playerId) {}
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
        this(registry, policy, ready, failures, null, null, null);
    }
    EventRuntime(Registry registry, Supplier<ScriptCapabilityPolicy> policy, BooleanSupplier ready,
                 Consumer<String> failures, BiFunction<ScriptValue.ItemDescriptor, String, ItemStack> dropStack,
                 Consumer<Runnable> nextTick, BiConsumer<String, Player> runCommand) {
        this.registry = registry; this.policy = policy; this.ready = ready; this.failures = failures;
        this.dropStack = dropStack;
        this.nextTick = nextTick;
        this.runCommand = runCommand;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockBreak(BlockBreakEvent event) { block("block_break", event.getPlayer(), event.getBlock(), event); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockPlace(BlockPlaceEvent event) { block("block_place", event.getPlayer(), event.getBlockPlaced(), event); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockDrop(BlockDropItemEvent event) {
        if (!interested("block_drop") || event.getItems().isEmpty()) return;
        var state = event.getBlockState();
        dispatch("block_drop", event.getPlayer(), blockFields(event.getPlayer(), state.getBlock(), state.getType().name()), event);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        var block = event.getClickedBlock();
        if (block != null && event.useInteractedBlock() != Event.Result.DENY && interested("block_interact"))
            dispatch("block_interact", event.getPlayer(), blockFields(event.getPlayer(), block, block.getType().name()), event);
        if (event.hasItem() && itemUseAllowed(event) && interested("item_interact")) {
            dispatch("item_interact", event.getPlayer(), itemFields(event.getPlayer(), event.getItem()), new Cancellable() {
                public boolean isCancelled() { return !itemUseAllowed(event); }
                public void setCancelled(boolean cancelled) { if (cancelled) event.setUseItemInHand(Event.Result.DENY); }
            });
        }
    }
    private static boolean itemUseAllowed(PlayerInteractEvent event) {
        return event.useItemInHand() != Event.Result.DENY
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.useInteractedBlock() != Event.Result.DENY);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !interested("entity_interact")) return;
        dispatch("entity_interact", event.getPlayer(), entityFields(event.getPlayer(), event.getRightClicked()), event);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (interested("item_consume")) dispatch("item_consume", event.getPlayer(), itemFields(event.getPlayer(), event.getItem()), event);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {
        if (interested("player_respawn"))
            dispatch("player_respawn", event.getPlayer(), locationFields(event.getPlayer(), event.getRespawnLocation()), null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !interested("player_teleport")) return;
        var fields = locationFields(event.getPlayer(), event.getTo());
        fields.put("from_world", text(event.getFrom().getWorld().getKey().toString()));
        fields.put("cause", text(event.getCause().name()));
        dispatch("player_teleport", event.getPlayer(), fields, event);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player) || !interested("projectile_hit")) return;
        var fields = locationFields(player, event.getEntity().getLocation());
        fields.put("projectile_type", text(event.getEntityType().name()));
        fields.put("hit_block_type", text(event.getHitBlock() == null ? "" : event.getHitBlock().getType().name()));
        fields.put("hit_entity_type", text(event.getHitEntity() == null ? "" : event.getHitEntity().getType().name()));
        dispatch("projectile_hit", player, fields, event);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent event) {
        if (!interested("player_command")) return;
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        int end = 1;
        while (end < message.length() && end <= 129 && !Character.isWhitespace(message.charAt(end))) end++;
        String label = message.substring(1, end).toLowerCase(Locale.ROOT);
        if (!label.matches("[a-z0-9_.:-]{1,128}") || label.equals("ais") || label.endsWith(":ais")) return;
        var fields = fields(event.getPlayer());
        fields.put("command_name", text(label));
        dispatch("player_command", event.getPlayer(), fields, event);
    }
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
        Map<String, ScriptValue> fields = locationFields(actor, entity.getLocation());
        identity(fields, "entity", entity);
        return fields;
    }
    private static Map<String, ScriptValue> locationFields(Player actor, Location location) {
        Map<String, ScriptValue> fields = fields(actor);
        fields.put("world", text(location.getWorld().getKey().toString()));
        fields.put("x", integer(location.getBlockX())); fields.put("y", integer(location.getBlockY())); fields.put("z", integer(location.getBlockZ()));
        return fields;
    }
    private static Map<String, ScriptValue> itemFields(Player actor, ItemStack item) {
        var fields = fields(actor);
        fields.put("item_type", text(item.getType().name()));
        String identity = item.getPersistentDataContainer().get(CUSTOM_ITEM, PersistentDataType.STRING);
        fields.put("item_id", text(identity == null || identity.length() > 129 ? "" : identity));
        return fields;
    }
    private static void identity(Map<String, ScriptValue> fields, String prefix, Entity entity) {
        fields.put(prefix + "_uuid", text(entity == null ? "" : entity.getUniqueId().toString()));
        fields.put(prefix + "_type", text(entity == null ? "" : entity.getType().name()));
    }
    private void block(String kind, Player player, Block block, Cancellable event) {
        if (event.isCancelled() || !interested(kind)) return;
        dispatch(kind, player, blockFields(player, block, block.getType().name()), event, block);
    }
    private static Map<String, ScriptValue> blockFields(Player player, Block block, String material) {
        Map<String, ScriptValue> fields = fields(player);
        fields.put("world", text(block.getWorld().getKey().toString()));
        fields.put("block_type", text(material));
        fields.put("x", integer(block.getX())); fields.put("y", integer(block.getY())); fields.put("z", integer(block.getZ()));
        return fields;
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
        ItemStack replacementDrop = null;
        for (Subscriber subscriber : handlers) {
            CompiledFeature feature = subscriber.feature();
            CompiledFeature.EventHandler handler = subscriber.handler();
            if (event != null && event.isCancelled()) return;
            if (!receives(player, handler)) continue;
            // Owner policy denial is expected after restrict; execution errors are reported separately.
            try { policy.get().validate(feature); }
            catch (ValidationException | SecurityException denied) { continue; }
            if (captured != registry.snapshot() || closed || !ready.getAsBoolean()) return;
            try {
                CompiledFeature.EventResult result = handler.evaluate(data);
                if (result.cancelled() && event != null) event.setCancelled(true);
                if (maximumHearts == 0) maximumHearts = result.blockHearts();
                if (replacementDrop == null && !result.cancelled() && event instanceof BlockDropItemEvent
                    && result.replacementDrop() != null && dropStack != null)
                    replacementDrop = dropStack.apply(result.replacementDrop(), feature.definition().id());
                if (result.runOwnCommand() && event != null && !event.isCancelled())
                    queueCommand(captured, subscriber, player, event);
                for (Feature.MessageAction message : result.messages()) player.sendMessage(Component.text(message.text()));
            } catch (RuntimeException failure) {
                report(feature.definition().id(), kind, failure);
            }
        }
        if (replacementDrop != null && event instanceof BlockDropItemEvent drop && !drop.isCancelled() && !drop.getItems().isEmpty()) {
            var items = drop.getItems();
            items.getFirst().setItemStack(replacementDrop);
            items.subList(1, items.size()).clear();
        }
        if (block != null && event instanceof BlockBreakEvent && !event.isCancelled() && maximumHearts > 0)
            hit(block, event, maximumHearts);
    }

    private static boolean receives(Player player, CompiledFeature.EventHandler handler) {
        return (handler.permission().equals("*") || player.hasPermission(handler.permission()))
            && (handler.bypassPermission() == null || !player.hasPermission(handler.bypassPermission()));
    }

    private void queueCommand(Registry.Snapshot captured, Subscriber subscriber, Player player, Cancellable event) {
        if (nextTick == null || pendingCommands.size() >= 128) return;
        var key = new PendingCommand(subscriber.feature().definition().id(), player.getUniqueId());
        if (!pendingCommands.add(key)) return;
        try {
            nextTick.accept(() -> {
                pendingCommands.remove(key);
                if (closed || !ready.getAsBoolean() || registry.snapshot() != captured || !player.isOnline()
                    || event.isCancelled()
                    || !receives(player, subscriber.handler())) return;
                try { runCommand.accept(key.featureId(), player); }
                catch (RuntimeException failure) { report(key.featureId(), subscriber.handler().kind(), failure); }
            });
        } catch (RuntimeException failure) {
            pendingCommands.remove(key);
            throw failure;
        }
    }

    private void report(String id, String kind, RuntimeException failure) {
        if (reported.add(id + ":" + kind)) failures.accept("Event handler failed for " + id + " (" + kind + "): "
            + FeatureManager.display(failure instanceof ValidationException ? failure.getMessage() : "RUNTIME_FAILED")
            + "; further failures suppressed until registry replacement");
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
