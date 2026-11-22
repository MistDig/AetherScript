package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import dev.aetherscript.paper.api.AddonService;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaperAddonsTest {
    private final Map<String,Plugin> plugins = new HashMap<>();
    private boolean primary = true, enabled = true;
    private int changes, calls;
    private final PluginManager manager = (PluginManager)Proxy.newProxyInstance(PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class}, (p,m,a) -> {
        if (m.getName().equals("getPlugin")) return plugins.get(a[0]); throw new AssertionError(m.getName());
    });
    private final Server server = (Server)Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (p,m,a) -> switch(m.getName()) {
        case "isPrimaryThread" -> primary;
        case "getPluginManager" -> manager;
        default -> throw new AssertionError(m.getName());
    });
    private Plugin plugin(String name) {
        Plugin plugin = (Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (p,m,a) -> switch(m.getName()) {
            case "getName" -> name;
            case "isEnabled" -> enabled;
            default -> throw new AssertionError(m.getName());
        });
        plugins.put(name,plugin); return plugin;
    }
    private AddonService.Callable callable(String name) {
        return new AddonService.Callable(name,List.of(ScriptType.TEXT),ScriptType.TEXT,false,"Echo text",(call,args) -> { calls++; return args.getFirst(); });
    }
    private InvocationContext context(Set<String> capabilities) {
        return new InvocationContext("test-feature","revision",null,capabilities,new ExecutionBudget(),()->true,(d,a,c)-> { throw new AssertionError(); },message -> {});
    }
    @Test void namespacedCapabilitiesAndDescribeAreInstalledAtomically() {
        var registry = new PaperAddons(server,List.of(),()->changes++);
        Plugin alpha = plugin("Alpha");
        registry.register(alpha,"alpha","1.0",List.of(callable("echo")));
        var descriptor = registry.descriptors().getFirst();
        assertEquals("alpha_echo",descriptor.dslName()); assertEquals(Set.of("addon.alpha.echo"),descriptor.requiredCapabilities());
        assertEquals("1.0",registry.installed().getFirst().version()); assertEquals(1,changes);
        assertThrows(IllegalArgumentException.class,()->registry.register(plugin("Other"),"alpha","2",List.of(callable("other"))));
        assertThrows(IllegalArgumentException.class,()->registry.register(plugin("Bad"),"bad","1",List.of(callable("same"),callable("same"))));
        assertEquals(1,registry.descriptors().size());
    }
    @Test void ownerDisableRevocationAndOldSnapshotsFailClosed() throws Exception {
        var registry = new PaperAddons(server,List.of(),()->changes++);
        Plugin owner = plugin("Addon"); registry.register(owner,"demo","1",List.of(callable("echo")));
        var old = registry.descriptors().getFirst();
        var args = List.of(new ScriptValue(ScriptType.TEXT,"ok"));
        assertThrows(ValidationException.class,()->registry.invoke(old,args,context(Set.of()),null)); assertEquals(0,calls);
        assertEquals("ok",registry.invoke(old,args,context(old.requiredCapabilities()),null).text());
        enabled = false;
        assertThrows(ValidationException.class,()->registry.invoke(old,args,context(old.requiredCapabilities()),null));
        enabled = true; registry.unregister(owner); registry.register(owner,"demo","1",List.of(callable("echo")));
        assertThrows(ValidationException.class,()->registry.invoke(old,args,context(old.requiredCapabilities()),null));
        assertEquals(1,calls);
    }
    @Test void registrationAndInvocationRequireMainThread() {
        var registry = new PaperAddons(server,List.of(),()->{}); Plugin owner = plugin("Addon"); primary = false;
        assertThrows(IllegalStateException.class,()->registry.register(owner,"demo","1",List.of(callable("echo"))));
    }
}
