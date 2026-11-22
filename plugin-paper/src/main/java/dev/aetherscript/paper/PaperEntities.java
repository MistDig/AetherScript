package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.*;
import java.util.*;
import java.util.function.Predicate;

/** Main-thread-only entity operations. UUID possession alone never grants authority. */
final class PaperEntities implements Listener, AutoCloseable {
    record Owner(String feature, String revision) {}
    private record Owned(Owner owner, Mob entity) {}
    private static final Set<String> TYPES = Set.of("zombie", "skeleton", "husk", "stray", "spider", "cave_spider", "slime");
    private final Server server;
    private final Map<UUID, Owned> entities = new HashMap<>();
    private record Membership(Owner owner, UUID player) {}
    private final Map<Membership, String> teams = new HashMap<>();
    private boolean closed;
    PaperEntities(Server server) { this.server = server; }
    ScriptValue invoke(String id, Player actor, List<ScriptValue> args, Owner owner) {
        if (closed) throw error("ENTITY_RUNTIME_CLOSED");
        return switch (id) {
            case "player.team.join" -> {
                String key = args.getFirst().text();
                if (!key.matches("[a-z][a-z0-9_]{0,63}")) throw error("TEAM_KEY_INVALID");
                Membership member = new Membership(owner, actor.getUniqueId());
                if (!teams.containsKey(member) && teams.size() >= 4096) throw error("TEAM_MEMBERSHIP_LIMIT");
                teams.put(member, key); yield unit();
            }
            case "player.team.is" -> bool(args.getFirst().text().equals(teams.get(new Membership(owner, actor.getUniqueId()))));
            case "player.team.leave" -> { teams.remove(new Membership(owner, actor.getUniqueId())); yield unit(); }
            case "entity.own.spawn" -> spawn(args, owner);
            case "entity.own.remove" -> {
                UUID uuid = uuid(args.getFirst().text());
                Owned owned = entities.get(uuid);
                if (owned == null) yield bool(false);
                requireOwner(owned, owner);
                remove(uuid);
                yield bool(true);
            }
            case "entity.own.health" -> {
                Mob mob = owned(args.getFirst().text(), owner);
                long hearts = args.get(1).integer();
                if (hearts < 1 || hearts > 100) throw error("ENTITY_HEALTH_LIMIT: expected 1..100 hearts");
                var health = mob.getAttribute(Attribute.MAX_HEALTH);
                if (health == null) throw error("ENTITY_HEALTH_UNAVAILABLE");
                health.setBaseValue(hearts * 2d); mob.setHealth(hearts * 2d);
                yield unit();
            }
            case "entity.own.target_player" -> {
                Mob mob = owned(args.getFirst().text(), owner);
                if (!actor.isOnline() || actor.isDead() || !mob.getWorld().equals(actor.getWorld())
                    || mob.getLocation().distanceSquared(actor.getLocation()) > 128d * 128d) yield bool(false);
                mob.setTarget(actor);
                yield bool(actor.equals(mob.getTarget()));
            }
            default -> throw error("UNKNOWN_ENTITY_OPERATION");
        };
    }
    private ScriptValue spawn(List<ScriptValue> args, Owner owner) {
        entities.entrySet().removeIf(entry -> !entry.getValue().entity().isValid());
        if (entities.size() >= 256 || entities.values().stream().filter(value -> value.owner().equals(owner)).count() >= 64)
            throw error("ENTITY_LIMIT: maximum 64 per feature revision and 256 total");
        var ref = args.getFirst().location();
        String type = args.get(1).text().replaceFirst("^minecraft:", "");
        if (!TYPES.contains(type)) throw error("ENTITY_TYPE_DENIED");
        String name = args.get(2).text();
        if (name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl)) throw error("ENTITY_NAME_LIMIT");
        World world = server.getWorld(ref.worldId());
        if (world == null) throw error("WORLD_UNAVAILABLE");
        if (Math.abs(ref.x()) > 30_000_000 || Math.abs(ref.z()) > 30_000_000 || ref.y() < world.getMinHeight() || ref.y() >= world.getMaxHeight())
            throw error("ENTITY_LOCATION_INVALID");
        if (!world.isChunkLoaded(((int)Math.floor(ref.x())) >> 4, ((int)Math.floor(ref.z())) >> 4)) throw error("CHUNK_UNAVAILABLE");
        EntityType entityType = EntityType.valueOf(type.toUpperCase(Locale.ROOT));
        @SuppressWarnings("unchecked") Class<? extends Mob> entityClass = (Class<? extends Mob>) entityType.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) throw error("ENTITY_TYPE_DENIED");
        Mob mob = world.spawn(new Location(world, ref.x(), ref.y(), ref.z(), ref.yaw(), ref.pitch()), entityClass, entity -> {
            entity.setPersistent(false); entity.setRemoveWhenFarAway(false); entity.setCanPickupItems(false);
            entity.customName(Component.text(name)); entity.setCustomNameVisible(!name.isBlank());
        });
        entities.put(mob.getUniqueId(), new Owned(owner, mob));
        return new ScriptValue(ScriptType.TEXT, mob.getUniqueId().toString());
    }
    private Mob owned(String value, Owner owner) {
        Owned owned = entities.get(uuid(value));
        if (owned == null) throw error("ENTITY_NOT_OWNED");
        requireOwner(owned, owner);
        if (!owned.entity().isValid()) { entities.remove(owned.entity().getUniqueId()); throw error("ENTITY_UNAVAILABLE"); }
        return owned.entity();
    }
    private static void requireOwner(Owned owned, Owner owner) {
        if (!owned.owner().equals(owner)) throw error("ENTITY_NOT_OWNED");
    }
    private static UUID uuid(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { throw error("ENTITY_ID_INVALID"); }
    }
    private void remove(UUID id) { Owned owned = entities.remove(id); if (owned != null) owned.entity().remove(); }
    void forget(UUID player) { teams.keySet().removeIf(member -> member.player().equals(player)); }
    void reconcile(Predicate<Owner> active) {
        teams.keySet().removeIf(member -> !active.test(member.owner()));
        entities.entrySet().stream().filter(entry -> !active.test(entry.getValue().owner())).map(Map.Entry::getKey).toList().forEach(this::remove);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void death(EntityDeathEvent event) { entities.remove(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void unload(ChunkUnloadEvent event) {
        entities.entrySet().stream().filter(entry -> {
            Location location = entry.getValue().entity().getLocation();
            return location.getWorld().equals(event.getWorld()) && location.getBlockX() >> 4 == event.getChunk().getX()
                && location.getBlockZ() >> 4 == event.getChunk().getZ();
        }).map(Map.Entry::getKey).toList().forEach(this::remove);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void unload(WorldUnloadEvent event) {
        entities.entrySet().stream().filter(entry -> entry.getValue().entity().getWorld().equals(event.getWorld())).map(Map.Entry::getKey).toList().forEach(this::remove);
    }
    @Override public void close() { closed = true; reconcile(owner -> false); HandlerList.unregisterAll(this); }
    private static ScriptValue bool(boolean value) { return new ScriptValue(ScriptType.BOOLEAN, value); }
    private static ScriptValue unit() { return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE); }
    private static ValidationException error(String code) { return new ValidationException("$", code); }
}
