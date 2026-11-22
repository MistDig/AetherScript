package dev.aetherscript.paper;

import dev.aetherscript.core.ValidationException;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class PaperExternalIntegrationsTest {
    @Test void currencyHasExplicitBoundsAndNoSilentRounding() {
        assertEquals(12345, PaperExternalIntegrations.minor(123.45));
        assertEquals(0, PaperExternalIntegrations.minor(0));
        assertEquals(0.01, PaperExternalIntegrations.major(1));
        assertEquals(1_000_000d, PaperExternalIntegrations.major(100_000_000));
        for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,0.001,1e20})
            assertThrows(ValidationException.class,()->PaperExternalIntegrations.minor(invalid));
        for(long invalid:new long[]{-1,0,100_000_001,Long.MAX_VALUE})
            assertThrows(ValidationException.class,()->PaperExternalIntegrations.major(invalid));
    }
    @Test void numericVersionLowerBoundsRejectUnparseableAndUnsafeRanges() {
        assertTrue(PaperExternalIntegrations.versionAtLeast("7.0.13-SNAPSHOT","7.0.12"));
        assertTrue(PaperExternalIntegrations.versionAtLeast("1.7.3-b131","1.7"));
        assertTrue(PaperExternalIntegrations.versionAtLeast("26.3-R0.1-SNAPSHOT","26.3"));
        assertTrue(PaperExternalIntegrations.versionAtLeast("26.3.build.245-stable","26.3"));
        assertFalse(PaperExternalIntegrations.versionAtLeast("7.0.9","7.0.10"));
        assertFalse(PaperExternalIntegrations.versionAtLeast("unknown","1"));
        assertFalse(PaperExternalIntegrations.versionAtLeast("7.0.1.2.3","7"));
        for(String invalid:new String[]{"",">=1","*","1-SNAPSHOT","99999999999"})
            assertThrows(ValidationException.class,()->PaperExternalIntegrations.versionAtLeast("7",invalid));
    }
    @Test void absentOptionalPluginsFailClearlyWhileAvailabilityIsFalse() {
        PluginManager plugins=(PluginManager)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PluginManager.class},(p,m,a)->{
            if(m.getName().equals("getPlugin")) return null; throw new AssertionError(m.getName());
        });
        Server server=(Server)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Server.class},(p,m,a)->{
            if(m.getName().equals("getPluginManager")) return plugins;
            if(m.getName().equals("getBukkitVersion")) return "26.3-R0.1-SNAPSHOT";
            throw new AssertionError(m.getName());
        });
        var bridge=new PaperExternalIntegrations(server);
        assertFalse(bridge.available("vault","1.7"));assertFalse(bridge.available("worldguard","7"));
        assertTrue(bridge.available("permissions","26.3"));
        assertTrue(assertThrows(ValidationException.class,()->bridge.balance(null)).reason().contains("MISSING_VAULT"));
        assertEquals("INTEGRATION_UNKNOWN_PROVIDER",bridge.health("arbitrary.class").status());
    }
    @Test void permissionChecksOnlyInvokingActorAndExplicitBoundedNodes() {
        Player player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
            if(m.getName().equals("hasPermission")) { assertEquals("example.use",a[0]);return true; } throw new AssertionError(m.getName());
        });
        Server server=(Server)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Server.class},(p,m,a)->{throw new AssertionError(m.getName());});
        var bridge=new PaperExternalIntegrations(server);
        assertTrue(bridge.permission(player,"example.use"));
        for(String invalid:new String[]{"*","example.*","Upper.case","", "a."+"b".repeat(128)})
            assertThrows(ValidationException.class,()->bridge.permission(player,invalid));
    }
}
