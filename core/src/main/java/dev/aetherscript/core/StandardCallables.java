package dev.aetherscript.core;

import java.util.*;
import static dev.aetherscript.core.CallableRegistry.ExecutionContext.*;

/** Fixed first persistent-home surface; no reflection or arbitrary platform APIs. */
public final class StandardCallables {
    private StandardCallables() {}
    public static CallableRegistry create() {
        CallableRegistry registry = new CallableRegistry();
        add(registry, "player.location.read", "player_location", List.of(), ScriptType.LOCATION, PAPER_MAIN, "player.location.read", "Return the invoking player's immutable location snapshot.");
        add(registry, "state.own.get_location", "state_get_location", List.of(ScriptType.TEXT), ScriptType.optional(ScriptType.LOCATION), ASYNC_IO, "state.own", "Read an optional location owned by this feature and player.");
        add(registry, "state.own.put_location", "state_put_location", List.of(ScriptType.TEXT, ScriptType.LOCATION), ScriptType.UNIT, ASYNC_IO, "state.own", "Durably store a location owned by this feature and player.");
        add(registry, "scheduler.delay", "delay", List.of(ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "scheduler.delay", "Resume after a bounded number of server ticks.");
        add(registry, "player.teleport.self", "teleport_self", List.of(ScriptType.LOCATION), ScriptType.UNIT, PAPER_MAIN, "player.teleport.self", "Teleport only the invoking player to a validated location.");
        add(registry, "state.own.save", "state_save", List.of(ScriptType.TEXT, ScriptType.UNIT), ScriptType.UNIT, ASYNC_IO, "state.own", "Save a bounded typed value T for this feature/player; T is inferred from argument 2. Unit is not persistable.");
        add(registry, "state.own.load", "state_load", List.of(ScriptType.TEXT, ScriptType.UNIT), ScriptType.UNIT, ASYNC_IO, "state.own", "Load a bounded typed value T, inferred from fallback argument 2; missing returns fallback and a stored type mismatch fails.");
        add(registry, "player.inventory.has", "inventory_has", List.of(ScriptType.ITEM), ScriptType.BOOLEAN, PAPER_MAIN, "player.inventory.read", "Check the invoking player's storage inventory for matching plain items.");
        add(registry, "player.inventory.give", "inventory_give", List.of(ScriptType.ITEM), ScriptType.BOOLEAN, PAPER_MAIN, "player.inventory.write", "Give plain items only if the full amount fits; false leaves inventory unchanged.");
        add(registry, "player.inventory.remove", "inventory_remove", List.of(ScriptType.ITEM), ScriptType.BOOLEAN, PAPER_MAIN, "player.inventory.write", "Remove plain items only if the full amount exists; false leaves inventory unchanged.");
        add(registry, "player.kit.claim", "kit_claim", List.of(ScriptType.TEXT, ScriptType.list(ScriptType.ITEM), ScriptType.INTEGER), ScriptType.TEXT, ASYNC_IO, "player.kit.claim", "Claim a bounded bundle with a durable feature/player/key cooldown in seconds; returns claimed, cooldown, or inventory_full.");
        return registry;
    }
    private static void add(CallableRegistry registry, String id, String name, List<ScriptType> parameters, ScriptType result,
                            CallableRegistry.ExecutionContext context, String capability, String documentation) {
        registry.register(new CallableRegistry.Descriptor(id, name, parameters, result, context, Set.of(capability), true,
                "Paper 26.3 build 245", "Host operation rejected or unavailable", documentation, "active feature revision"));
    }
}

