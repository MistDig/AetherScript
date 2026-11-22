package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaperEntitiesTest {
    private final Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (proxy, method, args) -> { throw new AssertionError(method.getName()); });
    private Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("getUniqueId")) return id;
            throw new AssertionError(method.getName());
        });
    }
    private ScriptValue text(String value) { return new ScriptValue(ScriptType.TEXT, value); }
    @Test void membershipIsIsolatedByPlayerFeatureAndRevisionAndRetired() {
        var runtime = new PaperEntities(server);
        var red = new PaperEntities.Owner("arena_red", "r1");
        var blue = new PaperEntities.Owner("arena_blue", "r1");
        var next = new PaperEntities.Owner("arena_red", "r2");
        Player alice = player(UUID.randomUUID()), bob = player(UUID.randomUUID());
        runtime.invoke("player.team.join", alice, List.of(text("red")), red);
        assertTrue(runtime.invoke("player.team.is", alice, List.of(text("red")), red).bool());
        assertFalse(runtime.invoke("player.team.is", bob, List.of(text("red")), red).bool());
        assertFalse(runtime.invoke("player.team.is", alice, List.of(text("red")), blue).bool());
        assertFalse(runtime.invoke("player.team.is", alice, List.of(text("red")), next).bool());
        runtime.invoke("player.team.join", alice, List.of(text("blue")), blue);
        runtime.reconcile(owner -> owner.equals(blue));
        assertFalse(runtime.invoke("player.team.is", alice, List.of(text("red")), red).bool());
        assertTrue(runtime.invoke("player.team.is", alice, List.of(text("blue")), blue).bool());
        runtime.forget(alice.getUniqueId());
        assertFalse(runtime.invoke("player.team.is", alice, List.of(text("blue")), blue).bool());
    }
    @Test void rejectsInvalidKeysIdsAndUnknownOperationsBeforeTouchingServer() {
        var runtime = new PaperEntities(server);
        var owner = new PaperEntities.Owner("arena", "r1");
        Player player = player(UUID.randomUUID());
        assertThrows(ValidationException.class, () -> runtime.invoke("player.team.join", player, List.of(text("../other")), owner));
        assertThrows(ValidationException.class, () -> runtime.invoke("entity.own.remove", player, List.of(text("bogus")), owner));
        assertFalse(runtime.invoke("entity.own.remove", player, List.of(text(UUID.randomUUID().toString())), owner).bool());
        assertThrows(ValidationException.class, () -> runtime.invoke("entity.arbitrary.remove", player, List.of(), owner));
    }
}
