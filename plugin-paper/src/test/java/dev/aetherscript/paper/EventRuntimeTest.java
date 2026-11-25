package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventRuntimeTest {
    @TempDir Path directory;
    private final Registry registry = new Registry();
    private final List<Component> messages = new ArrayList<>();
    private final Set<String> permissions = new HashSet<>();
    private final UUID playerId = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p,m,a) -> {
        if (m.getName().equals("hasPermission")) return permissions.contains(a[0]);
        if (m.getName().equals("sendMessage")) { messages.add((Component)a[0]); return null; }
        if (m.getName().equals("getUniqueId")) return playerId;
        if (m.getName().equals("isOnline")) return true;
        throw new UnsupportedOperationException(m.getName());
    });
    private static final class Cancellation implements Cancellable {
        boolean cancelled;
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean value) { assertTrue(value, "Handlers must never clear another protection decision"); cancelled = value; }
    }
    private CompiledFeature feature(String id, String message, String permission, String bypass, boolean cancel) {
        return feature(id, message, permission, bypass, cancel, "block_break");
    }
    private CompiledFeature feature(String id, String message, String permission, String bypass, boolean cancel, String kind) {
        var handler = new CompiledFeature.EventHandler() {
            public String kind() { return kind; }
            public String permission() { return permission; }
            public String bypassPermission() { return bypass; }
            public CompiledFeature.EventResult evaluate(CompiledFeature.EventData data) {
                if (kind.startsWith("block_")) assertEquals("STONE", data.fields().get("block_type").text());
                return new CompiledFeature.EventResult(List.of(new Feature.MessageAction(message)), cancel);
            }
        };
        var program = new CompiledFeature.Program() {
            public List<CompiledFeature.EventHandler> eventHandlers() { return List.of(handler); }
            public boolean eventOnly() { return true; }
            public List<Feature.MessageAction> evaluate(List<String> args) { throw new AssertionError(); }
        };
        return new CompiledFeature(new Feature(1,id,"1",List.of(ScriptCapabilityPolicy.MESSAGE),new Feature.Command(id,"demo.use","player",List.of())),message,"ir",program);
    }
    private Map<String,ScriptValue> fields() {
        Map<String,ScriptValue> result = new HashMap<>();
        for (var entry : CompiledFeature.eventFields("block_break").entrySet()) result.put(entry.getKey(), new ScriptValue(entry.getValue(), entry.getValue().equals(ScriptType.INTEGER) ? 1L : "STONE"));
        return result;
    }
    @Test void guestProtectionBypassAndPreviouslyCancelledEvents() {
        var runtime = new EventRuntime(registry, ScriptCapabilityPolicy::messageOnly, () -> true, fail -> fail(fail));
        registry.replace(0, Map.of("guard",feature("guard","denied","*","builder.bypass",true)));
        Cancellation cancellation = new Cancellation();
        runtime.dispatch("block_break",player,fields(),cancellation);
        assertTrue(cancellation.cancelled); assertEquals(List.of(Component.text("denied")),messages);
        messages.clear(); runtime.dispatch("block_break",player,fields(),cancellation); assertTrue(messages.isEmpty());
        permissions.add("builder.bypass"); runtime.dispatch("block_break",player,fields(),new Cancellation()); assertTrue(messages.isEmpty());
    }
    @Test void reloadDeliversExactlyOnceAndRetiresRemovedHandlers() {
        var runtime = new EventRuntime(registry, ScriptCapabilityPolicy::messageOnly, () -> true, fail -> fail(fail));
        registry.replace(0, Map.of("guard",feature("guard","old","*",null,false)));
        runtime.dispatch("block_break",player,fields(),new Cancellation());
        registry.replace(1, Map.of("guard",feature("guard","new","*",null,false)));
        runtime.dispatch("block_break",player,fields(),new Cancellation());
        registry.replace(2, Map.of()); runtime.dispatch("block_break",player,fields(),new Cancellation());
        assertEquals(List.of(Component.text("old"),Component.text("new")),messages);
    }
    @Test void permissionAndOwnerRestrictionAreIsolatedPerFeature() throws Exception {
        var policy = new ScriptCapabilityPolicy(directory.resolve("policy.json"),Set.of(ScriptCapabilityPolicy.MESSAGE));
        policy.grant("blocked",Set.of());
        var runtime = new EventRuntime(registry, () -> policy, () -> true, fail -> fail(fail));
        registry.replace(0, Map.of("blocked",feature("blocked","blocked","*",null,true),"ok",feature("ok","ok","demo.use",null,false)));
        runtime.dispatch("block_break",player,fields(),new Cancellation()); assertTrue(messages.isEmpty());
        permissions.add("demo.use"); runtime.dispatch("block_break",player,fields(),new Cancellation());
        assertEquals(List.of(Component.text("ok")),messages);
        policy.grant("ok",Set.of()); runtime.dispatch("block_break",player,fields(),new Cancellation()); assertEquals(1,messages.size());
    }
    @Test void lifecycleAndListenerPrioritiesProtectPlatformDecisions() throws Exception {
        for (String name : List.of("blockBreak", "blockPlace")) {
            java.lang.reflect.Method method = Arrays.stream(EventRuntime.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            var annotation = method.getAnnotation(org.bukkit.event.EventHandler.class);
            assertEquals(org.bukkit.event.EventPriority.HIGHEST, annotation.priority()); assertTrue(annotation.ignoreCancelled());
        }
        boolean[] ready = {false};
        var runtime = new EventRuntime(registry, ScriptCapabilityPolicy::messageOnly, () -> ready[0], fail -> fail(fail));
        registry.replace(0, Map.of("guard",feature("guard","hello","*",null,false)));
        runtime.dispatch("block_break",player,fields(),new Cancellation()); assertTrue(messages.isEmpty());
        ready[0] = true; runtime.dispatch("block_break",player,fields(),new Cancellation()); assertEquals(1,messages.size());
        runtime.close(); runtime.dispatch("block_break",player,fields(),new Cancellation()); assertEquals(1,messages.size());
    }
    @Test void joinQuitAndPlaceUseTheirTypedPayloads() {
        var runtime = new EventRuntime(registry, ScriptCapabilityPolicy::messageOnly, () -> true, fail -> fail(fail));
        for (String kind : List.of("join", "quit", "block_place")) {
            registry.replace(registry.snapshot().generation(), Map.of("guard", feature("guard",kind,"*",null,false,kind)));
            Map<String,ScriptValue> fields = new HashMap<>();
            for (var entry : CompiledFeature.eventFields(kind).entrySet()) fields.put(entry.getKey(),new ScriptValue(entry.getValue(),entry.getValue().equals(ScriptType.INTEGER) ? 1L : "STONE"));
            runtime.dispatch(kind,player,fields,kind.startsWith("block_") ? new Cancellation() : null);
        }
        assertEquals(List.of(Component.text("join"),Component.text("quit"),Component.text("block_place")),messages);
    }
    @Test void deterministicFeatureOrderStopsDeliveryAfterCancellation() {
        var runtime = new EventRuntime(registry, ScriptCapabilityPolicy::messageOnly, () -> true, fail -> fail(fail));
        registry.replace(0, Map.of("z-neighbor",feature("z-neighbor","later","*",null,false),
            "a-guard",feature("a-guard","denied","*",null,true)));
        Cancellation cancellation = new Cancellation();
        runtime.dispatch("block_break",player,fields(),cancellation);
        assertTrue(cancellation.cancelled); assertEquals(List.of(Component.text("denied")),messages);
    }

    @Test void queuedInteractionDeduplicatesAndChecksCancellationAndRevision() throws Exception {
        var feature = new FeatureParser().parse("""
            feature station version "1"
            command station permission "demo.use" sender player { send "Hello" }
            on block_interact permission "demo.use" { run_self() }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8), "aether");
        var caps = Set.copyOf(feature.definition().requires());
        var policy = new ScriptCapabilityPolicy(directory.resolve("station.json"), caps);
        policy.grant("station", caps);
        var nextTick = new ArrayDeque<Runnable>();
        var invoked = new ArrayList<String>();
        var runtime = new EventRuntime(registry, () -> policy, () -> true, failure -> fail(failure),
            null, nextTick::add, (id, actor) -> invoked.add(id));
        registry.replace(0, Map.of("station", feature));
        permissions.add("demo.use");
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, null, org.bukkit.block.BlockFace.UP);
        event.setUseInteractedBlock(Event.Result.DEFAULT);
        runtime.dispatch("block_interact", player, fields(), event);
        runtime.dispatch("block_interact", player, fields(), event);
        assertEquals(1, nextTick.size());
        event.setCancelled(true);
        nextTick.removeFirst().run();
        assertTrue(invoked.isEmpty());
        event.setUseInteractedBlock(Event.Result.DEFAULT);
        runtime.dispatch("block_interact", player, fields(), event);
        registry.replace(1, Map.of("station", feature));
        nextTick.removeFirst().run();
        assertTrue(invoked.isEmpty());
        runtime.dispatch("block_interact", player, fields(), event);
        nextTick.removeFirst().run();
        assertEquals(List.of("station"), invoked);
    }
}
