package example.sky;

import dev.aetherscript.core.ScriptType;
import dev.aetherscript.core.ScriptValue;
import dev.aetherscript.paper.api.AddonService;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A standalone Bukkit plugin. AetherScript and Paper classes are never bundled. */
public final class PersonalWeatherAddon extends JavaPlugin implements Listener {
    private record WeatherOverride(WeatherType previous, WeatherType applied, BukkitTask expiry) {}
    private final Map<UUID, WeatherOverride> overrides = new HashMap<>();
    private AddonService service;

    @Override public void onEnable() {
        service = getServer().getServicesManager().load(AddonService.class);
        if (service == null) throw new IllegalStateException("AetherScript addon service is unavailable");
        service.register(this, "sky", "1.0.0", List.of(new AddonService.Callable(
            "weather", List.of(ScriptType.TEXT), ScriptType.TEXT, true,
            "Set invoking-player personal weather to rain/clear for 200 ticks, or reset it. "
                + "At most 128 active overrides. Requires addon.sky.weather; no global weather change.",
            (call, arguments) -> weather(call.player(), arguments.getFirst().text()))));
        getServer().getPluginManager().registerEvents(this, this);
    }

    private ScriptValue weather(Player player, String mode) {
        if (mode.equals("reset")) {
            restore(player);
            return text("Personal weather reset.");
        }
        if (!mode.equals("rain") && !mode.equals("clear"))
            throw new IllegalArgumentException("Expected rain, clear or reset");
        UUID id = player.getUniqueId();
        WeatherOverride old = overrides.get(id);
        if (old == null && overrides.size() >= 128)
            throw new IllegalStateException("Personal weather override limit reached");
        WeatherType previous = old == null ? player.getPlayerWeather() : old.previous();
        if (old != null) old.expiry().cancel();
        WeatherType selected = mode.equals("rain") ? WeatherType.DOWNFALL : WeatherType.CLEAR;
        player.setPlayerWeather(selected);
        BukkitTask expiry = getServer().getScheduler().runTaskLater(this, () -> restore(player), 200L);
        overrides.put(id, new WeatherOverride(previous, selected, expiry));
        return text("Personal weather set to " + mode + " for 200 ticks.");
    }

    private void restore(Player player) {
        WeatherOverride old = overrides.remove(player.getUniqueId());
        if (old == null) return;
        old.expiry().cancel();
        // Preserve a later, different personal weather change made by another plugin.
        if (player.getPlayerWeather() != old.applied()) return;
        if (old.previous() == null) player.resetPlayerWeather();
        else player.setPlayerWeather(old.previous());
    }

    @EventHandler public void quit(PlayerQuitEvent event) { restore(event.getPlayer()); }

    @Override public void onDisable() {
        if (service != null) service.unregister(this);
        for (UUID id : List.copyOf(overrides.keySet())) {
            Player player = getServer().getPlayer(id);
            if (player != null) restore(player);
        }
        overrides.values().forEach(value -> value.expiry().cancel());
        overrides.clear();
    }

    private static ScriptValue text(String value) { return new ScriptValue(ScriptType.TEXT, value); }
}
