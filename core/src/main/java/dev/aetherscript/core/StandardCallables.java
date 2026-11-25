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
        add(registry, "scheduler.repeat", "repeat_wait", List.of(ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "scheduler.repeat", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.from_location", "world_from_location", List.of(ScriptType.LOCATION), ScriptType.WORLD, PAPER_MAIN, "world.read", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.block.at", "block_at", List.of(ScriptType.WORLD, ScriptType.INTEGER, ScriptType.INTEGER, ScriptType.INTEGER), ScriptType.BLOCK, PAPER_MAIN, "world.read", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.block.material", "block_material", List.of(ScriptType.BLOCK), ScriptType.TEXT, PAPER_MAIN, "world.read", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.block.set", "block_set", List.of(ScriptType.BLOCK, ScriptType.TEXT), ScriptType.UNIT, PAPER_MAIN, "world.block.write", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.time.read", "world_time", List.of(ScriptType.WORLD), ScriptType.INTEGER, PAPER_MAIN, "world.read", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.time.set", "world_set_time", List.of(ScriptType.WORLD, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "world.time.write", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.weather.read", "world_weather", List.of(ScriptType.WORLD), ScriptType.TEXT, PAPER_MAIN, "world.read", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "world.weather.set", "world_set_weather", List.of(ScriptType.WORLD, ScriptType.TEXT, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "world.weather.write", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.actionbar", "actionbar", List.of(ScriptType.TEXT), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.title", "title", List.of(ScriptType.TEXT, ScriptType.TEXT, ScriptType.INTEGER, ScriptType.INTEGER, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.sound", "sound", List.of(ScriptType.TEXT, ScriptType.INTEGER, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "player.sound", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.bossbar", "bossbar", List.of(ScriptType.TEXT, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.bossbar.clear", "bossbar_clear", List.of(), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.scoreboard", "scoreboard", List.of(ScriptType.list(ScriptType.TEXT)), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "player.display.scoreboard.clear", "scoreboard_clear", List.of(), ScriptType.UNIT, PAPER_MAIN, "player.display", "Bounded local Paper operation; requires an active player and feature.");
        add(registry, "state.shared.read", "shared_read", List.of(ScriptType.TEXT, ScriptType.TEXT, ScriptType.UNIT), ScriptType.UNIT, ASYNC_IO, "state.shared.read", "Read a typed value T in an authorized shared scope; infer T from fallback argument 3. Missing values return fallback; stored type mismatch fails.");
        add(registry, "state.shared.commit", "shared_commit", List.of(ScriptType.TEXT, ScriptType.map(ScriptType.TEXT, ScriptType.UNIT), ScriptType.list(ScriptType.TEXT)), ScriptType.BOOLEAN, ASYNC_IO, "state.shared.write", "Atomically commit a bounded map of typed writes and list of deleted keys in an authorized shared scope; infer T from writes argument 2. Unit is not persistable.");
        add(registry, "player.identity.self", "player_key", List.of(), ScriptType.TEXT, PAPER_MAIN, "player.identity.self", "Return the invoking player's stable storage key: p followed by the lowercase UUID without hyphens.");
        add(registry, "player.menu.choose", "menu_choose", List.of(ScriptType.TEXT, ScriptType.list(ScriptType.ITEM)), ScriptType.INTEGER, PAPER_MAIN, "player.menu", "Open a read-only selection menu of 1..54 items; wait for a zero-based clicked slot or -1 on close. Selection grants no authority.");
        add(registry, "player.inventory.exchange", "inventory_exchange", List.of(ScriptType.ITEM, ScriptType.ITEM), ScriptType.BOOLEAN, PAPER_MAIN, "player.inventory.exchange", "Atomically remove payment and give reward in the invoking player's storage; false leaves inventory unchanged.");
        add(registry, "player.inventory.exchange_many", "inventory_exchange_many",
            List.of(ScriptType.list(ScriptType.ITEM), ScriptType.list(ScriptType.ITEM)), ScriptType.BOOLEAN, PAPER_MAIN,
            "player.inventory.exchange", "Atomically exchange bundles of 1..16 payments and rewards in the invoking player's storage. Missing items or space leave inventory unchanged.");
        add(registry, "entity.own.spawn", "entity_spawn", List.of(ScriptType.LOCATION, ScriptType.TEXT, ScriptType.TEXT), ScriptType.TEXT, PAPER_MAIN, "entity.own", "Spawn a bounded allowlisted feature-owned mob; return its UUID text.");
        add(registry, "entity.own.remove", "entity_remove", List.of(ScriptType.TEXT), ScriptType.BOOLEAN, PAPER_MAIN, "entity.own", "Remove only a mob owned by this feature and active revision.");
        add(registry, "entity.own.health", "entity_health", List.of(ScriptType.TEXT, ScriptType.INTEGER), ScriptType.UNIT, PAPER_MAIN, "entity.own", "Set an owned mob's health to 1..100 hearts.");
        add(registry, "entity.own.target_player", "entity_target_player", List.of(ScriptType.TEXT), ScriptType.BOOLEAN, PAPER_MAIN, "entity.own", "Target only the invoking player with an owned mob.");
        add(registry, "player.team.join", "team_join", List.of(ScriptType.TEXT), ScriptType.UNIT, PAPER_MAIN, "player.team", "Join a bounded feature-owned team on the player's scoreboard.");
        add(registry, "player.team.is", "team_is", List.of(ScriptType.TEXT), ScriptType.BOOLEAN, PAPER_MAIN, "player.team", "Test membership in this feature's team.");
        add(registry, "player.team.leave", "team_leave", List.of(), ScriptType.UNIT, PAPER_MAIN, "player.team", "Leave this feature's team.");
        add(registry, "integration.inspect", "integration_available", List.of(ScriptType.TEXT, ScriptType.TEXT), ScriptType.BOOLEAN, PAPER_MAIN, "integration.inspect", "Test an optional vault, permissions or worldguard provider against a numeric minimum version; missing/incompatible returns false.");
        add(registry, "integration.economy.balance", "external_balance", List.of(), ScriptType.INTEGER, PAPER_MAIN, "integration.economy.read", "Read invoking player's Vault economy balance as exact minor units (100 per major unit); unsupported precision fails.");
        add(registry, "integration.economy.deposit", "external_deposit", List.of(ScriptType.INTEGER), ScriptType.BOOLEAN, PAPER_MAIN, "integration.economy.write", "Deposit 1..100000000 minor units only to invoking player's Vault account; provider transaction failure returns false.");
        add(registry, "integration.economy.withdraw", "external_withdraw", List.of(ScriptType.INTEGER), ScriptType.BOOLEAN, PAPER_MAIN, "integration.economy.write", "Withdraw 1..100000000 minor units only from invoking player's Vault account; provider transaction failure returns false.");
        add(registry, "integration.permission.check", "external_permission", List.of(ScriptType.TEXT), ScriptType.BOOLEAN, PAPER_MAIN, "integration.permission", "Read invoking player's Bukkit permission; explicit lowercase dotted nodes only, max128, no wildcard.");
        add(registry, "integration.worldguard.build", "external_can_build", List.of(ScriptType.LOCATION), ScriptType.BOOLEAN, PAPER_MAIN, "integration.worldguard.read", "Read WorldGuard7 region testBuild result for invoking player in their current world. Does not include session bypass or other protection plugins.");
        return registry;
    }
    private static void add(CallableRegistry registry, String id, String name, List<ScriptType> parameters, ScriptType result,
                            CallableRegistry.ExecutionContext context, String capability, String documentation) {
        registry.register(new CallableRegistry.Descriptor(id, name, parameters, result, context, Set.of(capability), true,
                "Paper 26.3 build 245", "Host operation rejected or unavailable", documentation, "active feature revision"));
    }
}

