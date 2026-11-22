package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;

/** Optional adapters. All class and method names are fixed here, never supplied by scripts.
 * Caller dispatches on Paper's main thread after invocation/capability authorization. */
final class PaperExternalIntegrations {
    static final long MAX_MINOR = 100_000_000L;
    record Health(String provider, String version, String status) { boolean available() { return status.equals("ready"); } }
    private final Server server;
    PaperExternalIntegrations(Server server) { this.server = Objects.requireNonNull(server); }
    static ValidationException error(String code) { return new ValidationException("$", code); }
    static long minor(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw error("INTEGRATION_BALANCE_INVALID");
        try { return BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact(); }
        catch (ArithmeticException failure) { throw error("INTEGRATION_BALANCE_PRECISION_OR_RANGE"); }
    }
    static double major(long minor) {
        if (minor < 1 || minor > MAX_MINOR) throw error("INTEGRATION_AMOUNT_MUST_BE_1_TO_100000000_MINOR");
        return BigDecimal.valueOf(minor, 2).doubleValue();
    }
    static boolean versionAtLeast(String actual, String minimum) {
        if (minimum == null || !minimum.matches("[0-9]{1,9}(?:\\.[0-9]{1,9}){0,3}")) throw error("INTEGRATION_MINIMUM_VERSION_INVALID");
        if (actual == null) return false;
        var matcher = java.util.regex.Pattern.compile("^([0-9]{1,9}(?:\\.[0-9]{1,9}){0,3})(?:[^0-9.].*|\\.[A-Za-z].*)?$").matcher(actual);
        if (!matcher.matches()) return false;
        String[] a = matcher.group(1).split("\\."), b = minimum.split("\\.");
        for (int i=0; i<Math.max(a.length,b.length); i++) {
            long left=i<a.length?Long.parseLong(a[i]):0, right=i<b.length?Long.parseLong(b[i]):0;
            if (left != right) return left > right;
        }
        return true;
    }
    private Plugin plugin(String name) {
        Plugin plugin=server.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) throw error("INTEGRATION_MISSING_" + name.toUpperCase(Locale.ROOT));
        return plugin;
    }
    private record Economy(Object provider, Class<?> api, Class<?> response) {}
    @SuppressWarnings({"rawtypes", "unchecked"}) private Economy economy() throws ReflectiveOperationException {
        Plugin vault=plugin("Vault"); ClassLoader loader=vault.getClass().getClassLoader();
        Class<?> api=Class.forName("net.milkbowl.vault.economy.Economy",false,loader);
        Class<?> response=Class.forName("net.milkbowl.vault.economy.EconomyResponse",false,loader);
        var registration=server.getServicesManager().getRegistration((Class)api);
        if (registration == null) throw error("INTEGRATION_MISSING_ECONOMY_PROVIDER");
        Object provider=registration.getProvider();
        if (!api.isInstance(provider) || !Boolean.TRUE.equals(api.getMethod("isEnabled").invoke(provider))) throw error("INTEGRATION_INCOMPATIBLE_ECONOMY_PROVIDER");
        int digits=(Integer)api.getMethod("fractionalDigits").invoke(provider);
        if (digits != -1 && digits < 2) throw error("INTEGRATION_ECONOMY_REQUIRES_TWO_DECIMAL_PRECISION");
        api.getMethod("getBalance", OfflinePlayer.class); api.getMethod("depositPlayer", OfflinePlayer.class,double.class); api.getMethod("withdrawPlayer",OfflinePlayer.class,double.class); response.getMethod("transactionSuccess");
        return new Economy(provider,api,response);
    }
    Health health(String provider) {
        try {
            return switch(provider) {
                case "permissions" -> new Health("Bukkit permissions",server.getBukkitVersion(),"ready");
                case "vault" -> { var economy=economy(); Plugin vault=plugin("Vault"); yield new Health((String)economy.api().getMethod("getName").invoke(economy.provider()),vault.getPluginMeta().getVersion(),"ready"); }
                case "worldguard" -> { Plugin guard=plugin("WorldGuard"); worldGuardMethods(guard); yield new Health("WorldGuard",guard.getPluginMeta().getVersion(),"ready"); }
                default -> throw error("INTEGRATION_UNKNOWN_PROVIDER");
            };
        } catch (ValidationException missing) { return new Health(provider,"",missing.reason()); }
        catch (ReflectiveOperationException | LinkageError incompatible) { return new Health(provider,"","INTEGRATION_INCOMPATIBLE_API"); }
    }
    boolean available(String provider,String minimum) { versionAtLeast("0",minimum); var health=health(provider); return health.available() && versionAtLeast(health.version(),minimum); }
    long balance(Player actor) {
        try { var e=economy(); return minor((Double)e.api().getMethod("getBalance",OfflinePlayer.class).invoke(e.provider(),actor)); }
        catch (ReflectiveOperationException | LinkageError failure) { throw error("INTEGRATION_INCOMPATIBLE_ECONOMY_API"); }
    }
    boolean transaction(Player actor,long amount,boolean deposit) {
        double value=major(amount);
        try {
            var e=economy(); Object response=e.api().getMethod(deposit?"depositPlayer":"withdrawPlayer",OfflinePlayer.class,double.class).invoke(e.provider(),actor,value);
            if (!e.response().isInstance(response)) throw error("INTEGRATION_INCOMPATIBLE_ECONOMY_RESPONSE");
            return (Boolean)e.response().getMethod("transactionSuccess").invoke(response);
        } catch (ReflectiveOperationException | LinkageError failure) { throw error("INTEGRATION_INCOMPATIBLE_ECONOMY_API"); }
    }
    boolean permission(Player actor,String node) {
        if (node == null || node.length()>128 || !node.matches("[a-z][a-z0-9_-]*(?:\\.[a-z0-9_-]+)+")) throw error("INTEGRATION_PERMISSION_NODE_INVALID");
        return actor.hasPermission(node);
    }
    private record GuardMethods(Class<?> guard,Class<?> adapter,Class<?> bukkitPlugin,Class<?> localPlayer,Class<?> weLocation,Class<?> stateFlag,Class<?> regionQuery) {}
    private GuardMethods worldGuardMethods(Plugin plugin) throws ReflectiveOperationException {
        ClassLoader loader=plugin.getClass().getClassLoader();
        var g=new GuardMethods(Class.forName("com.sk89q.worldguard.WorldGuard",false,loader),Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter",false,loader),Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin",false,loader),Class.forName("com.sk89q.worldguard.LocalPlayer",false,loader),Class.forName("com.sk89q.worldedit.util.Location",false,loader),Class.forName("com.sk89q.worldguard.protection.flags.StateFlag",false,loader),Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery",false,loader));
        g.guard().getMethod("getInstance");g.adapter().getMethod("adapt",Location.class);g.bukkitPlugin().getMethod("inst");g.bukkitPlugin().getMethod("wrapPlayer",Player.class);
        g.regionQuery().getMethod("testBuild",g.weLocation(),g.localPlayer(),Array.newInstance(g.stateFlag(),0).getClass());
        Class<?> platform=Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform",false,loader);
        Class<?> container=Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer",false,loader);
        g.guard().getMethod("getPlatform"); platform.getMethod("getRegionContainer"); container.getMethod("createQuery");
        return g;
    }
    boolean canBuild(Player actor,Location location) {
        if (location.getWorld()==null || !actor.getWorld().equals(location.getWorld())) throw error("INTEGRATION_LOCATION_MUST_BE_ACTOR_WORLD");
        try {
            Plugin plugin=plugin("WorldGuard"); var g=worldGuardMethods(plugin);
            Object instance=g.guard().getMethod("getInstance").invoke(null);
            // Resolve through pinned public interface types, not provider implementation methods.
            ClassLoader loader=plugin.getClass().getClassLoader();
            Class<?> platform=Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform",false,loader);
            Class<?> container=Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer",false,loader);
            Object p=g.guard().getMethod("getPlatform").invoke(instance);
            Object regions=platform.getMethod("getRegionContainer").invoke(p);
            Object query=container.getMethod("createQuery").invoke(regions);
            Object local=g.bukkitPlugin().getMethod("wrapPlayer",Player.class).invoke(g.bukkitPlugin().getMethod("inst").invoke(null),actor);
            Object point=g.adapter().getMethod("adapt",Location.class).invoke(null,location);
            return (Boolean)g.regionQuery().getMethod("testBuild",g.weLocation(),g.localPlayer(),Array.newInstance(g.stateFlag(),0).getClass()).invoke(query,point,local,Array.newInstance(g.stateFlag(),0));
        } catch (ReflectiveOperationException | LinkageError failure) { throw error("INTEGRATION_INCOMPATIBLE_WORLDGUARD_API"); }
    }
}
