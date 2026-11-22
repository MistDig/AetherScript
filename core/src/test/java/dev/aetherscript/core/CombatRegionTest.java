package dev.aetherscript.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRegionTest {
    private CompiledFeature compile(String declarations) {
        return new FeatureParser().parse(("feature arena version \"1\"\n" + declarations).getBytes(StandardCharsets.UTF_8), "aether");
    }
    private CompiledFeature.EventData data(String kind) {
        var fields = new LinkedHashMap<String, ScriptValue>();
        CompiledFeature.eventFields(kind).forEach((name, type) -> fields.put(name, new ScriptValue(type, type == ScriptType.INTEGER ? 7L : "snapshot")));
        return new CompiledFeature.EventData(kind, fields);
    }
    @Test void combatKindsExposeTypedSnapshotsAndBoundCancellation() {
        for (String kind : List.of("entity_damage", "entity_target", "entity_death")) {
            boolean cancellable = !kind.equals("entity_death");
            var feature = compile("on " + kind + " permission \"arena.play\" {\nsend entity_type + text(x)\n" + (cancellable ? "cancel\n" : "") + "}");
            var result = feature.program().eventHandlers().getFirst().evaluate(data(kind));
            assertEquals(cancellable, result.cancelled());
            assertEquals("snapshot7", result.messages().getFirst().text());
            assertTrue(feature.definition().requires().contains("runtime.event." + kind));
        }
        assertThrows(ValidationException.class, () -> compile("on entity_death permission \"*\" {\ncancel\n}"));
        assertThrows(ValidationException.class, () -> compile("on entity_damage permission \"*\" {\nsend killer_uuid\n}"));
        assertThrows(ValidationException.class, () -> compile("on entity_target permission \"*\" {\nblock_hearts 1\n}"));
        assertThrows(IllegalArgumentException.class, () -> new CompiledFeature.EventData("entity_damage", Map.of()));
    }
    @Test void regionNormalizesCornersAndUsesFloorAtNegativeBlockBoundary() throws Exception {
        var feature = compile("command arena permission \"arena.play\" sender player {\nlet p = player_location()\nsend text(inside_region(p, 2, 3, 4, -1, -2, -3))\n}");
        for (double x : new double[] {-1.01, -1, 2.99, 3}) {
            var messages = new ArrayList<String>();
            var ctx = new InvocationContext("arena", "revision", UUID.randomUUID(), Set.copyOf(feature.definition().requires()), new ExecutionBudget(), () -> true,
                (d,a,c) -> new ScriptValue(ScriptType.LOCATION, new ScriptValue.LocationSnapshot(UUID.randomUUID(), x, 0, 0, 0, 0)), messages::add);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) { executor.submit(() -> feature.program().evaluate(List.of(), ctx)).get(); }
            assertEquals(List.of(Boolean.toString(x >= -1 && x < 3)), messages);
        }
        assertThrows(ValidationException.class, () -> compile("command arena permission \"arena.play\" sender player {\nlet x = inside_region(1, 0, 0, 0, 1, 1, 1)\n}"));
    }
    @Test void entityAndTeamCatalogHasNarrowTypedAuthority() {
        var feature = compile("command arena permission \"arena.play\" sender player {\nlet mob = entity_spawn(player_location(), \"zombie\", \"Boss\")\nentity_health(mob, 20)\nlet targeted = entity_target_player(mob)\nlet removed = entity_remove(mob)\nteam_join(\"red\")\nlet red = team_is(\"red\")\nteam_leave()\n}");
        assertEquals(Set.of("runtime.message.send", "player.location.read", "entity.own", "player.team"), Set.copyOf(feature.definition().requires()));
        assertThrows(ValidationException.class, () -> compile("command arena permission \"arena.play\" sender player {\nentity_health(1, 20)\n}"));
    }
}
