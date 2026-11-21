package dev.aetherscript.paper;

import dev.aetherscript.core.ScriptType;
import dev.aetherscript.core.ScriptValue;
import dev.aetherscript.core.ValidationException;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Main-thread world calls and transient, revision-owned player displays. */
final class PaperWorldDisplays {
    record Owner(String feature, String revision) {}
    private record Bar(Owner owner, BossBar value) {}
    private record Sidebar(Owner owner, Scoreboard previous, Scoreboard view) {}
    private final Server server;
    private final Map<UUID, Bar> bars = new HashMap<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();

    PaperWorldDisplays(Server server) { this.server = server; }

    ScriptValue invoke(String id, Player player, List<ScriptValue> args, Owner owner) {
        switch (id) {
            case "world.from_location": {
                var world = new ScriptValue.WorldRef(args.getFirst().location().worldId());
                return new ScriptValue(ScriptType.WORLD, world);
            }
            case "world.block.at": {
                var block = new ScriptValue.BlockRef(args.getFirst().world().worldId(), number(args, 1, -30_000_000, 30_000_000),
                    number(args, 2, -20_000_000, 20_000_000), number(args, 3, -30_000_000, 30_000_000));
                return new ScriptValue(ScriptType.BLOCK, block);
            }
            case "world.block.material":
                return new ScriptValue(ScriptType.TEXT, block(args.getFirst().block()).getType().getKey().toString());
            case "world.block.set": {
                String key = args.get(1).text();
                Material material = key.matches("minecraft:[a-z0-9_]{1,128}") ? Material.matchMaterial(key) : null;
                if (material == null || !material.isBlock()) throw error("BLOCK_MATERIAL_INVALID");
                block(args.getFirst().block()).setType(material, false);
                break;
            }
            case "world.time.read":
                return new ScriptValue(ScriptType.INTEGER, world(args.getFirst().world().worldId()).getTime());
            case "world.time.set":
                world(args.getFirst().world().worldId()).setTime(number(args, 1, 0, 23999));
                break;
            case "world.weather.read": {
                World world = world(args.getFirst().world().worldId());
                return new ScriptValue(ScriptType.TEXT, world.isThundering() ? "thunder" : world.hasStorm() ? "rain" : "clear");
            }
            case "world.weather.set": {
                World world = world(args.getFirst().world().worldId());
                String weather = args.get(1).text();
                if (!List.of("clear", "rain", "thunder").contains(weather)) throw error("WEATHER_INVALID");
                int ticks = number(args, 2, 1, 72000);
                world.setStorm(!weather.equals("clear"));
                world.setThundering(weather.equals("thunder"));
                world.setWeatherDuration(ticks);
                world.setThunderDuration(ticks);
                if (weather.equals("clear")) world.setClearWeatherDuration(ticks);
                break;
            }
            case "player.display.actionbar":
                player.sendActionBar(component(args.getFirst(), 256));
                break;
            case "player.display.title":
                player.showTitle(Title.title(component(args.getFirst(), 256), component(args.get(1), 256),
                    Title.Times.times(duration(args, 2), duration(args, 3), duration(args, 4))));
                break;
            case "player.sound": {
                String key = args.getFirst().text();
                if (!key.matches("minecraft:[a-z0-9_.]{1,128}")) throw error("SOUND_KEY_INVALID");
                player.playSound(player.getLocation(), key, number(args, 1, 0, 100) / 100f, number(args, 2, 50, 200) / 100f);
                break;
            }
            case "player.display.bossbar": {
                Component label = component(args.getFirst(), 256);
                float progress = number(args, 1, 0, 100) / 100f;
                Bar previous = bars.get(player.getUniqueId());
                if (previous != null && previous.owner().equals(owner)) {
                    previous.value().name(label).progress(progress);
                } else {
                    clearBar(player.getUniqueId());
                    BossBar bar = BossBar.bossBar(label, progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    bars.put(player.getUniqueId(), new Bar(owner, bar));
                }
                break;
            }
            case "player.display.bossbar.clear":
                Bar bar = bars.get(player.getUniqueId());
                if (bar != null && bar.owner().equals(owner)) clearBar(player.getUniqueId());
                break;
            case "player.display.scoreboard":
                scoreboard(player, args.getFirst().list(), owner);
                break;
            case "player.display.scoreboard.clear":
                Sidebar sidebar = sidebars.get(player.getUniqueId());
                if (sidebar != null && sidebar.owner().equals(owner)) clearSidebar(player.getUniqueId());
                break;
            default:
                throw error("UNKNOWN_WORLD_DISPLAY_OPERATION");
        }
        return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE);
    }

    private void scoreboard(Player player, List<ScriptValue> lines, Owner owner) {
        if (lines.size() > 15) throw error("SCOREBOARD_LIMIT: expected at most 15 lines");
        var manager = Objects.requireNonNull(server.getScoreboardManager());
        Scoreboard view = manager.getNewScoreboard();
        var objective = view.registerNewObjective("aether", Criteria.DUMMY, Component.text("AetherScript"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int index = 0; index < lines.size(); index++) {
            String entry = "\u00a7" + Integer.toHexString(index);
            var team = view.registerNewTeam("line" + index);
            team.prefix(component(lines.get(index), 128));
            team.addEntry(entry);
            objective.getScore(entry).setScore(lines.size() - index);
        }
        UUID id = player.getUniqueId();
        clearSidebar(id);
        Scoreboard previous = player.getScoreboard();
        player.setScoreboard(view);
        sidebars.put(id, new Sidebar(owner, previous, view));
    }

    private World world(UUID id) {
        World world = server.getWorld(id);
        if (world == null) throw error("WORLD_UNAVAILABLE");
        return world;
    }
    private Block block(ScriptValue.BlockRef ref) {
        World world = world(ref.worldId());
        if (ref.y() < world.getMinHeight() || ref.y() >= world.getMaxHeight()) throw error("BLOCK_HEIGHT_INVALID");
        if (!world.isChunkLoaded(ref.x() >> 4, ref.z() >> 4)) throw error("CHUNK_UNAVAILABLE");
        return world.getBlockAt(ref.x(), ref.y(), ref.z());
    }
    private static int number(List<ScriptValue> args, int index, int min, int max) {
        long value = args.get(index).integer();
        if (value < min || value > max) throw error("VALUE_LIMIT: expected " + min + ".." + max);
        return (int) value;
    }
    private static Component component(ScriptValue value, int max) {
        if (value.text().length() > max) throw error("DISPLAY_TEXT_LIMIT: expected at most " + max + " characters");
        return Component.text(value.text());
    }
    private static Duration duration(List<ScriptValue> args, int index) {
        return Duration.ofMillis(50L * number(args, index, 0, 1200));
    }
    private void clearBar(UUID id) {
        Bar bar = bars.remove(id);
        Player player = server.getPlayer(id);
        if (bar != null && player != null) player.hideBossBar(bar.value());
    }
    private void clearSidebar(UUID id) {
        Sidebar sidebar = sidebars.remove(id);
        Player player = server.getPlayer(id);
        if (sidebar != null && player != null && player.getScoreboard() == sidebar.view()) player.setScoreboard(sidebar.previous());
    }
    void forget(UUID id) { clearBar(id); clearSidebar(id); }
    void reconcile(Predicate<Owner> active) {
        bars.entrySet().stream().filter(entry -> !active.test(entry.getValue().owner())).map(Map.Entry::getKey).toList().forEach(this::clearBar);
        sidebars.entrySet().stream().filter(entry -> !active.test(entry.getValue().owner())).map(Map.Entry::getKey).toList().forEach(this::clearSidebar);
    }
    private static ValidationException error(String code) { return new ValidationException("$", code); }
}
