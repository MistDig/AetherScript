# AetherScript AI reference

## Current capabilities

This document describes the 0.0.23 language foundation; verification remains attributed to its recorded version. It supports native .aether message commands, typed positional arguments, immutable and mutable locals, bounded loops, typed functions, immutable homogeneous lists/maps/sets, arithmetic, boolean conditions, explicit reload, revisions and rollback. A locally approved native player-command slice adds location snapshots, own-player/feature persistent homes, bounded delay and self teleport. Native player join/quit and cancellable block break/place handlers are available. Item descriptors, storage-inventory operations and durable kit claims are available. Typed records and locally installed multi-file packages support reusable quest logic and persisted progress. Economy, custom-item metadata and menus remain planned. Do not invent syntax or promise unsupported gameplay.

Use tool discovery from the connected app as the authority for available tools. Hosted and local gateways expose different tools. A hosted service version may differ from the plugin release version.

## AI authoring priority

AI authors should use canonical syntax, the versioned machine-readable authoring contract and precise source-located diagnostics. Human understanding of the DSL is not a prerequisite. AI-first means dependable generation and validation; it does not mean MCP-first delivery. The [17 November month plan](https://github.com/MistDig/AetherScript/blob/main/docs/ecosystem/month-goal-2026-11-17.md) puts language, core Minecraft APIs and addon foundations first, with MCP expansion last. Existing validators inherit shared core language changes; this batch adds no MCP tools or relay behavior. Discover the installed server contract rather than assuming newer syntax is installed.

## Tools by connection mode

Hosted OAuth MCP exposes these eight tools:

- aether_capabilities {}
- aether_server_status {}
- aether_scripts_list {}
- aether_scripts_read {id}
- aether_scripts_validate {id, source}
- aether_scripts_apply {id, source, expectedHash}
- aether_scripts_test {id, expectedMessages, arguments?}
- aether_scripts_test_status {id, testId}

`aether_capabilities` publishes the complete callable/type authoring guide and server-authorized execution capabilities, targets and test boundaries. Use it before writing native host code. The local gateway also exposes aether_validate {source, format} and aether_test {source, format, expectedMessages, arguments?}. Offline and loopback status modes have four tools and cannot deploy; a management relay profile has ten. Local discovery retains the guide when the server is unavailable and explicitly identifies older connections needing fresh approval.

format is aether, yaml or json for local validation/testing. Remote management uses native .aether files. arguments is an optional list of strings, including strings for integer and boolean inputs. expectedMessages is an ordered list of exact message strings.

## Permissions and autonomous edits

Read aether_server_status and aether_scripts_list. The live status includes allScripts and targets.
When allScripts is true, you may create any valid native feature ID and edit existing native scripts within the user's request. An empty list of existing scripts does not remove permission to create one.
When allScripts is false, use only the granted IDs. Explain the need for fresh owner approval if the requested ID is outside that grant.
Choose a sensible ID when the user authorizes creation but supplies no name. Do not ask for an additional approval already granted by the user and server. Never claim a denied operation succeeded.
Feature IDs are lowercase, start with a letter, and contain only lowercase letters, digits and hyphens, up to 64 characters. The source filename must match the ID. Full native access does not overwrite existing YAML/JSON sources or bypass source-format collisions.

Fresh 0.0.17 browser approval selects all or selected native feature IDs, an explicit execution capability set and, optionally, one test-player UUID. The AI may create, edit and activate native features within that grant without another per-edit approval. The approved execution set is persisted for those features so installed commands keep running after the AI disconnects. `ais permit` and `ais restrict` remain manual owner overrides; a browser grant cannot override an explicit console ceiling. Legacy personal-relay and older browser grants remain message-only until fresh browser approval; they never expand automatically.

## Safe editing workflow

1. Read the target with aether_scripts_read.
2. For creation, confirm exists=false and use expectedHash "ABSENT". For edits use the returned hash.
3. Write a complete valid native source. Keep explicit permission and sender rules.
4. Validate with aether_scripts_validate, then apply with aether_scripts_apply.
5. Read back source, hash and activeHash. Confirm the intended source is active.
6. Test using aether_scripts_test with explicit expectedMessages and arguments.
7. If pending, poll aether_scripts_test_status with the same id and returned testId. Report actualMessages, matched, effects, error and the active revision.

`aether_scripts_test` uses the owner-approved online player for native or player-only commands; the player must hold the command's declared permission. The AI cannot choose or replace that actor. Tests can send messages, write own-player state, wait and teleport the approved player: these are real effects. A start response may return `status: "pending"` and `testId`; poll `aether_scripts_test_status` with `{id,testId}` until completion, then report actual messages, errors, effects and comparison. A pure `sender any` message program without an approved actor retains synthetic compatibility. Offline `aether_test` is always synthetic and host calls fail with `HOST_CONTEXT_REQUIRED`.

On a stale source hash, read again before merging. After a timeout or unknown network outcome, read back before retrying because the change may already be committed. Invalid edits preserve the working revision. A test mismatch is not success.

## Complete runnable example

Save as greet.aether. Paper registers the real command name from the header. Direct server-console dispatch, replacement and restart are tested. /ais run greet remains available; connected-player command-tree visibility has not yet been verified.

```aether
feature greet version "1.0.0"
command greet permission "aetherscript.example.greet" sender any {
  arg name: text
  arg excited: boolean = true
  if excited {
    send "Hello " + name + "!"
  } else {
    send "Hello " + name
  }
}
```

Test the active feature with:
{"id":"greet","arguments":["MistDig"],"expectedMessages":["Hello MistDig!"]}
Then test:
{"id":"greet","arguments":["Alex","false"],"expectedMessages":["Hello Alex"]}
A player invokes /ais run greet "Alex Smith". Administrator console commands omit the leading slash.

## Syntax and limits

Declare arg name: text, arg count: integer = 1 or arg enabled: boolean = true at the start of a command. Required arguments precede defaults. At most 16 arguments, each at most 2048 UTF-16 units. Integer inputs are signed 64-bit decimal strings; booleans are exactly true or false. Quoted console arguments can contain spaces.

let creates an immutable inferred typed value. if requires a boolean. send requires text. Use text(value) for explicit scalar conversion; lists cannot be converted directly. Operators include +, -, *, /, %, comparisons, equality, !, && and ||. Braces delimit blocks; put statements on separate lines. Comments begin //.
Commands with arguments, var/set, loops or function declarations evaluate with fresh local state per invocation. Legacy scripts using only let, if and send without arguments preserve preparation-time evaluation and semantic hashes. Both branches are type-checked. Message-only evaluation buffers output, so arithmetic errors, budget failures and invalid arguments fail before message delivery. Host-backed commands stream sends as execution reaches them; later failure does not undo completed effects.
Source limit is 64 KiB locally. The hosted tool's source schema allows at most 16000 characters and its serialized request has a smaller transport envelope than the compiler's source limit. Keep remote scripts comfortably within both limits.
Messages are plain text, at most 2048 UTF-16 units each and at most 32 emitted messages per invocation. Conditional or zero-iteration paths may emit none. No arbitrary host objects, Java calls or general console execution.


## Mutable locals, loops and functions

var count = 3 declares an inferred mutable local. set count = count - 1 updates the nearest visible mutable binding with the same type. let, arguments and function parameters remain immutable. Locals support text, integer, boolean and homogeneous lists; state is fresh for every invocation and function call.

while condition { ... } requires a boolean. repeat count { ... } evaluates a nonnegative integer count once. break exits the nearest enclosing loop; continue skips to its next iteration. These keywords are valid only inside loops.

Declare fn name(parameter: integer): integer { ... } between the feature header and command. Parameters and results declare text, integer, boolean, `list<elementType>`, `map<K,V>`, `set<T>`, uuid, location, unit or `optional<T>` types. Call functions in expressions, for example text(double(3)). Every path of a non-unit function must return a matching value with `return expression`; a unit function may fall through or use bare `return`. Functions can call earlier declarations or themselves, but cannot call later declarations or capture command locals. Trailing parameters may declare typed constant defaults, for example fn double(value: integer = 2): integer { return value * 2 }. Required parameters precede defaults. Defaults cannot reference names or call user functions; immutable typed constants are shared safely across fresh frames. Limits are 32 functions, 16 parameters per function and 32 active function calls. Loops, expressions and nested calls share one 8192-step invocation budget and one 32-message output budget.

Save as countdown.aether:

```aether
feature countdown version "1.0.0"
fn label(value: integer): text {
  return "Count " + text(value)
}
command countdown permission "aetherscript.example.countdown" sender any {
  var remaining = 3
  while remaining > 0 {
    send label(remaining)
    set remaining = remaining - 1
  }
  send "Done"
}
```

Test with {"id":"countdown","expectedMessages":["Count 3","Count 2","Count 1","Done"]}. Run /ais run countdown twice; both invocations start at 3.

PersistentVariables provides the JSON host backend; SQLiteVariables provides a separate transactional SQLite backend. Paper binds typed per-player save/load and the own-location operations below to SQLite on its bounded I/O worker. `var` remains invocation-local. Other hosts select their backend; no automatic JSON-to-SQLite migration is performed. Real command registration and this runtime slice do not establish completion of the broader gameplay ecosystem.

## Immutable lists

Lists contain values of one type: `[1, 2, 3]`, `["red", "blue"]` or `[[1], [2]]`. Write an empty list with its element type: `list<integer>()` or `list<list<text>>()`. List types such as `list<integer>` are accepted for function parameters and results. Command arguments still accept only `text`, `integer` and `boolean`.

`values[0]` reads the first element; indices must be integers within bounds. `length(values)` returns an integer. `append(values, value)` returns a new list with a matching element type. Lists are immutable: use `var values = list<integer>()` followed by `set values = append(values, 1)` to replace a local list value. There is no indexed assignment or arbitrary host-array access. Lists contain at most 256 elements each and share an eight-level nesting limit with maps, sets, and optionals.

`==` and `!=` compare matching list types structurally, including nested elements. List concatenation with `+` and `text(listValue)` are rejected. Convert individual scalar elements with `text(value)` when building messages. Feature functions named `length` or `append` take precedence over these built-ins.

`for value in values { ... }` evaluates its list once and visits its elements in order. The loop variable is immutable and scoped to the body; it may shadow an outer name. `break` and `continue` target the nearest loop. Iteration and structural equality share the existing 8192-step invocation budget with expressions and function calls. Records, closures and modules remain unavailable. Maps and sets are described below.

Save as `scores.aether`:

```aether
feature scores version "1.0.0"
fn total(values: list<integer>): integer {
  var result = 0
  for value in values {
    set result = result + value
  }
  return result
}
command scores permission "aetherscript.example.scores" sender any {
  var values = list<integer>()
  set values = append(values, 1)
  set values = append(values, 2)
  set values = append(values, 3)
  send text(total(values))
}
```

Test with `{"id":"scores","expectedMessages":["6"]}`. Each invocation emits `6` and starts with fresh local state.

## Typed per-player storage (0.0.19)

`state_save(key, value)` durably saves a typed value and returns `unit`. `state_load(key, fallback)` returns the saved value with the exact type of `fallback`, or the fallback when the key is absent. Both require `state.own` and a player invocation. Keys match `[a-z][a-z0-9_]{0,63}` and are isolated by feature ID and invoking player UUID. A saved type mismatch fails with `STATE_TYPE_MISMATCH`; changing a fallback does not migrate existing data. Immutable nested lists, maps, sets, UUIDs, locations and optionals retain their types. Values exclude `unit`, including nested `unit`. Encoded values are capped at 4096 ASCII bytes and 4096 nodes; exceeding codec bounds fails with `STATE_VALUE_LIMIT`. The same key cannot change type. Legacy location keys and typed keys cannot be read or overwritten through the other API; incompatible or corrupt encoding fails with `STATE_TYPE_MISMATCH`. The existing `state_get_location` and `state_put_location` calls remain supported.

Copy `examples/player-progress.aether` into the scripts directory. Approve locally with `ais permit player-progress state.own`, then validate/reload it and grant `aetherscript.example.progress` to the test players. `/progress show` initially sends `No saved progress.`. `/progress save` sends `Progress saved: score=7, quest_stage=2.`. A later `/progress show` sends `Score: 7` then `Quest stage: 2`. Another player's initial result remains `No saved progress.`. Saved progress survives reload and normal restart under the same feature ID.

```aether
feature player-progress version "1.0.0"
command progress permission "aetherscript.example.progress" sender player {
  arg action: text = "show"
  if action == "save" {
    state_save("progress", {"score": 7, "quest_stage": 2})
    send "Progress saved: score=7, quest_stage=2."
  } else {
    let progress = state_load("progress", map<text, integer>())
    if contains_key(progress, "score") {
      send "Score: " + text(unwrap(get(progress, "score")))
      send "Quest stage: " + text(unwrap(get(progress, "quest_stage")))
    } else {
      send "No saved progress."
    }
  }
}
```

Storage runs on the bounded worker in `player-state/variables.sqlite`. Read/modify/write sequences can fail with `STATE_CONFLICT`; effects are ordered and do not form a transaction across calls. Cancellation rejects unaccepted writes; an accepted write can finish after cancellation. Back up player state with the server stopped. Source rollback does not roll back player data. Deletion, automatic migration, feature-global script state and a general transaction API remain planned.

## Immutable maps and sets (0.0.18 foundation)

Use `map<keyType, valueType>` and `set<elementType>` in typed signatures. A nonempty map is `{"red": 1, "blue": 2}`; an empty map is `map<text, integer>()`. A nonempty set is `set(1, 2, 1)`; an empty set is `set<integer>()`. Sets deduplicate and retain first insertion order. Map keys and set elements must be `integer`, `text`, `boolean`, `uuid` or `location`. Map values may use supported nested value types. Decimal numbers, records and collection command arguments are not supported; CLI arguments remain text/integer/boolean.

| Expression | Result |
| --- | --- |
| `put(entries, key, value)` | New map; replacement preserves the key's position. |
| `get(entries, key)` | `optional<valueType>`; absent keys return empty. Check `is_some` before `unwrap`. |
| `contains_key(entries, key)` | Boolean membership. |
| `keys(entries)` / `values(entries)` | Lists in map insertion order. |
| `remove(entries, key)` | New map without the key. |
| `add(members, value)` | New set; an existing member keeps its position. |
| `contains(members, value)` | Boolean membership. |
| `remove(members, value)` | New set without the member. |
| `length(collection)` | Integer size for list, map or set. |

Collections are immutable; replace a mutable local with `set entries = put(entries, "green", 3)`. Removing then adding a key/member puts it last. Duplicate map-literal keys fail with a source location during evaluation. `for member in members { ... }` visits a set in insertion order; iterate maps through `keys(entries)` or `values(entries)`. Direct map iteration is rejected. Matching maps/sets compare with `==`/`!=` independent of insertion order; lists remain ordered. Nested collection and optional equality, membership and collection copies consume the shared 8192-step invocation budget. Each collection has at most 256 entries/elements and collection type nesting is limited to eight. Limits apply to nested list/map/set/optional types. No indexed map assignment, collection concatenation or `text(collection)` conversion is provided. Feature functions with built-in names take precedence; use unambiguous names in generated code.

```aether
feature palette version "1.0.0"
command palette permission "aetherscript.example.palette" sender any {
  var counts = map<text, integer>()
  set counts = put(counts, "red", 2)
  let selected = set("red", "blue", "red")
  for name in selected {
    if contains_key(counts, name) {
      send name + ": " + text(unwrap(get(counts, name)))
    }
  }
}
```

Expected messages: `["red: 2"]`. This example is a specification for this batch; current-build verification is recorded separately by release evidence.

## Locally approved host commands

The default callable registry exposes these operations. The public compiler accepts `FeatureParser(CallableRegistry)` and freezes a registry snapshot for compilation. This is a compiler extension seam, not a public addon loader, event or serializer SDK. Requirements are inferred from native source, including calls inside helpers. No arbitrary Java/Bukkit objects, reflection, server commands or filesystem access are exposed.

| Call | Result | Execution capability |
| --- | --- | --- |
| `player_location()` | `location` | `player.location.read` |
| `state_get_location(name: text)` | `optional<location>` | `state.own` |
| `state_put_location(name: text, value: location)` | `unit` | `state.own` |
| `delay(ticks: integer)` | `unit` | `scheduler.delay` |
| `teleport_self(destination: location)` | `unit` | `player.teleport.self` |

A location is an immutable world-UUID/coordinates/yaw/pitch snapshot, not a live platform object. `is_some(value)` accepts an optional and returns boolean. `unwrap(value)` returns its element or fails with `OPTIONAL_EMPTY`; check presence first. Standalone calls must return unit. There is no general optional/location constructor or host-value command argument; command arguments remain text/integer/boolean.

Install `examples/homes.aether` locally as `scripts/homes.aether`. Actual server-console workflow:

```text
ais permit homes player.location.read,state.own,scheduler.delay,player.teleport.self
ais capabilities homes
ais validate homes
ais reload homes
```

Wait for `CAPABILITIES_SAVED` before validating/reloading. `permit` replaces the prior ceiling and includes `runtime.message.send`; `restrict homes` restores message-only execution and retires incompatible active work. Manual console approval does not activate source; use reload. A browser-authorized AI apply activates through the durable apply pipeline. Policy is persisted in `script-capabilities.json`; corrupt policy falls back to message-only until repaired. Players need the example's `aetherscript.example.home` permission. Use `/home set main`, then `/home go main`; this is a local player test, not an MCP synthetic test.

Player commands and approved native MCP tests use this host runtime. Delay accepts 0â€“1200 server ticks; helper functions can suspend and resume. Storage runs on the bounded I/O worker, waiting uses virtual threads, and player effects re-enter Paper's main thread. There are no event handlers, persistent scheduled jobs or offline-player effects. Limits are 120 seconds per invocation, 32 active globally, four per player and eight per feature, plus interpreter/output budgets.

Player disconnect, shutdown, source-revision replacement/retirement and removal of required capabilities retire pending work. Revoking an AI connection cancels its pending tests and prevents later remote operations; it does not unload installed scripts or erase their persisted execution policy. A reconnect never revives the invocation. Effects recheck the session, revision, capability ceiling and command permission. Teleport loads its target chunk asynchronously and guards again before the effect. Cancellation cannot undo already completed sends, writes or teleports.

State keys use the feature ID, invoking player UUID and name matching `[a-z][a-z0-9_]{0,63}`. Locations persist in `player-state/variables.sqlite` across normal restarts and revisions of the same feature. A concurrent modification after a read can fail with `STATE_CONFLICT`. No script deletion API or automatic JSON migration is included. Back up player-state with the server stopped; script revision rollback does not restore player state. This foundation does not complete broader gameplay or the planned optional model integration.

## Errors and connection recovery

Read isError and the response body. Validation diagnostics include a location in the message; local validation also returns line, column, path and reason. Correct the source and validate again.
TEXT_SOURCE_REQUIRED means the owner must resolve an existing legacy/duplicate source format. TARGET_DENIED means the requested ID is outside selected permissions.
HTTP 401 invalid_token requires reconnecting with a fresh ais connect browser approval. Superseded, expired, rejected and revoked sessions terminate. HTTP 503 temporarily_unavailable with Retry-After means a valid new connection is waiting for Paper; retry after the indicated delay.
The owner can inspect ais connections and revoke with ais disconnect. Approval does not imply the connection has reached Paper; Connected is confirmed after an actual status response.

## World actions, timers and displays (0.0.22)

A world reference holds a world UUID. A block reference holds that UUID and integer coordinates. Reading or setting a block requires a loaded world and chunk. Explicit console grants authorize world writes. A direct world write does not run player break/place checks. New capabilities are available to locally approved scripts.

| Call | Capability | Behavior |
| --- | --- | --- |
| `repeat_wait(integer) -> unit` | `scheduler.repeat` | Wait 1..1200 server ticks. |
| `world_from_location(location) -> world` | `world.read` | Extract the world UUID. |
| `block_at(world, integer, integer, integer) -> block` | `world.read` | Create a coordinate reference without loading terrain. |
| `block_material(block) -> text` | `world.read` | Read a namespaced material in a loaded chunk. |
| `block_set(block, text) -> unit` | `world.block.write` | Set a vanilla block in a loaded chunk without physics. |
| `world_time(world) -> integer` | `world.read` | Read time of day. |
| `world_set_time(world, integer) -> unit` | `world.time.write` | Set time to 0..23999. |
| `world_weather(world) -> text` | `world.read` | Read clear, rain or thunder. |
| `world_set_weather(world, text, integer) -> unit` | `world.weather.write` | Set clear, rain or thunder for 1..72000 ticks. |
| `actionbar(text) -> unit` | `player.display` | Show up to 256 characters to the invoking player. |
| `title(text, text, integer, integer, integer) -> unit` | `player.display` | Show title and subtitle, with fade-in, stay and fade-out in 0..1200 ticks each. |
| `sound(text, integer, integer) -> unit` | `player.sound` | Play a namespaced vanilla sound, volume 0..100 and pitch 50..200 as percentages. |
| `bossbar(text, integer) -> unit` | `player.display` | Show a boss bar with progress 0..100. |
| `bossbar_clear() -> unit` | `player.display` | Clear this revision’s boss bar. |
| `scoreboard(list<text>) -> unit` | `player.display` | Show at most 15 lines of up to 128 characters each. |
| `scoreboard_clear() -> unit` | `player.display` | Clear this revision’s scoreboard and restore the previous scoreboard when still active. |

`every 20 repeat 3 { ... }` waits 20 ticks before each of three iterations. Use it in player commands or functions. Interval and count are evaluated once. Invocations obey the existing deadline and retire on revision replacement, restriction or logout. Boss bars and scoreboards clear on feature retirement or logout.

Install the ten-heart example into `scripts/`, then run `ais permit ten-heart-blocks runtime.event.block_break,runtime.event.block_hearts` and `ais reload ten-heart-blocks` from the console. The first nine eligible breaks show remaining hearts. The tenth follows Paper’s normal break path. Progress and displays clear on reload or chunk unload.


```aether
feature ten-heart-blocks version "1"
on block_break permission "*" {
  if world == "minecraft:overworld" && block_type == "STONE" {
    block_hearts(10)
  }
}
```

```aether
feature world-display version "1"
command world-display permission "aetherscript.example.worlddisplay" sender player {
  let world = world_from_location(player_location())
  send "Weather: " + world_weather(world)
  bossbar("AetherScript", 100)
  scoreboard(["World dashboard"])
  every 10 repeat 2 {
    actionbar("AetherScript world dashboard")
  }
}
```

## Shared state and atomic transactions (0.0.24)

`shared_read(scope, key, fallback)` reads a feature-owned typed value and observes that key's revision for the current invocation. Scope is `"server"` or `"world"`; world scope uses the player's world at invocation start. Other features cannot access those keys. An absent or deleted key returns the supplied fallback. A stored value with a different type fails explicitly.

`shared_commit(scope, writes, deletes)` attempts one atomic transaction: `writes` is a homogeneous `map<text, T>`, and `deletes` is a `list<text>`. The operation compares every key previously observed in that scope, then commits all writes and deletions together. Read each key whose state matters before attempting a commit. It returns `false` if a revision changed; no partial update or automatic retry occurs. A script may tell the player to retry. Re-read and re-check business conditions on a fresh attempt. Deletions retain revision tombstones, so deleting and recreating a key cannot conceal a conflict. This provides atomic shared-state updates; it does not make inventory, displays or other external effects transactional.

`player_key()` returns `p` followed by the invoking player's UUID without hyphens. Use it to create per-player keys inside shared state. The calls require `state.shared.read`, `state.shared.write` and `player.identity.self` respectively. This slice is locally approved; hosted MCP grants do not acquire these capabilities automatically.

The economy example prices a virtual purchase at 5 credits. New players start with a fallback balance of 10; the feature starts with one unit of stock. It reads stock, balance and purchase count, then writes all three in one transaction. No actual inventory item is delivered. Install `economy.aether` and approve from the console:

```text
ais permit economy state.shared.read,state.shared.write,player.identity.self
ais validate economy
ais reload economy
```

With two fresh players, `/ais run economy show` reports `Stock: 1`, `Balance: 10`, `Owned: 0`. Race `/ais run economy buy` from both players. Exactly one purchase commits. The other observes sold-out stock or a conflict. Afterwards, the winner sees stock 0, balance 5 and owned 1; the loser sees stock 0, balance 10 and owned 0. Repeated purchases leave balances unchanged while stock is zero. Verify those values after restart as a separate persistence check.


A player invocation can observe up to 128 keys. Shared storage has a 4096-key limit including deletion tombstones, and an encoded value is limited to 4096 bytes. An accepted transaction may finish after the invocation retires.

```aether
feature economy version "1.0.0"
command economy permission "aetherscript.example.economy" sender player {
  arg action: text = "show"
  let owner = player_key()
  let balance_key = owner + "_balance"
  let owned_key = owner + "_owned"
  let stock = shared_read("server", "stock", 1)
  let balance = shared_read("server", balance_key, 10)
  let owned = shared_read("server", owned_key, 0)
  if action == "buy" {
    if stock < 1 {
      send "Sold out."
    } else {
      if balance < 5 {
        send "Insufficient balance."
      } else {
        let writes = put(put({"stock": stock - 1}, balance_key, balance - 5), owned_key, owned + 1)
        if shared_commit("server", writes, list<text>()) {
          send "Purchase committed."
        } else {
          send "Purchase conflict; try again."
        }
      }
    }
  } else {
    send "Stock: " + text(stock)
    send "Balance: " + text(balance)
    send "Owned: " + text(owned)
  }
}
```


## Entities, combat and regions (0.0.26)



Use `entity_spawn(location, type, name) -> text` to create an owned entity and receive its UUID. `entity_health(uuid, hearts)` sets health, `entity_target_player(uuid) -> boolean` requests targeting of the invoking player, and `entity_remove(uuid) -> boolean` removes an owned entity. All four require `entity.own`. UUID strings are references; they grant no authority over another feature's entity or an entity from a retired revision. The supported types are zombie, skeleton, husk, stray, spider, cave_spider and slime. Names have at most 64 characters without controls; health ranges from 1 to 100 hearts. Limits are 64 entities per active feature revision and 256 globally. Spawning never loads a chunk.



Owned entities are transient and nonpersistent. Reload, restriction, disable and chunk/world unload clean them up. A UUID saved in durable state does not preserve the entity or transfer ownership to a new revision. Treat a saved UUID as a potentially stale reference. A successful spawn is separate from a later shared-state commit; compensate an unsuccessful commit by removing the entity you just spawned.



`team_join(key)`, `team_is(key) -> boolean` and `team_leave()` require `player.team`. A key matches `[a-z][a-z0-9_]{0,63}`. Membership belongs to a feature, revision and player; it is transient and clears on quit. The global membership cap is 4096. These are script team memberships: membership alone does not prevent friendly fire, enforce regions or change another plugin's teams.



`inside_region(location, x1, y1, z1, x2, y2, z2) -> boolean` is a pure command expression. It floors location coordinates, normalizes reversed corners and includes boundary blocks. It checks coordinates only; choose world scope or check world identity separately when that matters. It does not consult an installed region-protection plugin.



### Combat event snapshots



`on entity_damage`, `on entity_target` and `on entity_death` require an explicit permission and infer `runtime.event.entity_damage`, `runtime.event.entity_target` and `runtime.event.entity_death`. Common immutable fields are `player_name`, `player_uuid`, `world`, `entity_uuid`, `entity_type` (text) and `x`, `y`, `z` (integer). Damage adds `cause`, `attacker_uuid`, `attacker_type` (text) and `damage_milli` (integer). Targeting adds `target_uuid`, `target_type`, `reason` (text). Death adds `killer_uuid`, `killer_type` (text), with empty strings when no killer is available.



The event actor determines permission checking and message delivery. Damage uses the player victim, otherwise the player attacker. Death uses the player victim, otherwise the player killer. Targeting uses the player target. A qualifying player must exist for a handler to run. Entity damage and target handlers support `cancel` with `runtime.event.cancel`; entity death does not. Cancellation never clears cancellation made by another plugin. These handlers remain synchronous and pure: scalar expressions, messages and permitted cancellation are supported, but state writes, team calls and entity calls are unavailable. A death handler cannot increment a score in this release.



### Two independent arenas



Install `examples/two-arenas.aether`. The `red` arena covers x/z 0–15 and the `blue` arena covers x 32–47/z 0–15, each at block y -64–320. Use two players in the same world and provide a safe, already loaded play area. Run these console commands:



```text

ais permit two-arenas player.location.read,entity.own,player.team,state.shared.read,state.shared.write,runtime.event.entity_damage,runtime.event.entity_death,runtime.event.cancel

ais validate two-arenas

ais reload two-arenas

```



Grant `aetherscript.example.arenas` only to the intended example operators. Player one runs `/ais run two-arenas red join`, moves inside red, then runs `/ais run two-arenas red start`. Player two does the same for blue. The start response includes the distinct boss UUID. `show` reports the selected arena's score, stored boss UUID, membership and whether the player is inside its bounds. World-scoped shared keys isolate scores and boss references between worlds.



Run `red score` while a red member inside red: red advances by one and blue stays unchanged. This is an explicit operator scoring command, not a death reward. Damage and death produce observable messages without changing scores. Run `red cleanup` to remove red's current boss and leave the invoking player's team; blue's boss and score remain. The example's permission allows either arena's cleanup, so it is an operator permission. Cleanup does not clear another player's membership or delete historical score data. Reload clears runtime memberships and owned entities while durable scores remain; stored boss UUIDs become stale and the next start replaces them.



The sample demonstrates independent bookkeeping and owned entities. It does not automatically prevent cross-arena combat or friendly fire, assign kills to arenas, or award score from a death event. Those acceptance criteria remain open. Verify each claimed gameplay effect on Paper separately from compiler validation.


## Owner-installed Java addons (0.0.27)

An addon is a trusted Bukkit Java plugin installed by the server owner. It obtains `dev.aetherscript.paper.api.AddonService` through Bukkit's service manager and registers a versioned namespace of typed callables. Each callable declares parameter/result `ScriptType`s, whether a player is required, documentation and a handler returning `ScriptValue`. The handler receives the invoking feature ID, revision, actor UUID and player. Registration is a main-thread atomic batch, and names cannot shadow builtins or another addon.

A namespace `sky` and callable `weather` produce `sky_weather(text) -> text` and capability/ID `addon.sky.weather`. Scripts need explicit grants for the functions they call, in addition to their command permission. The installed descriptor catalogue provides the signatures used for compilation and discovery; obtain it from the connected server rather than guessing from an external package's name. Registration does not grant scripts access automatically. Call handlers execute on the Paper main thread, so bound work and keep blocking I/O out of them.

The standalone PersonalWeatherAddon example uses the actual Paper player-weather API. Its script command requires `aetherscript.example.personalweather`; its sole addon grant is `addon.sky.weather`. `sky_weather("rain")` or `sky_weather("clear")` changes only the invoking player's weather for 200 server ticks; `sky_weather("reset")` restores their previous personal weather immediately. The addon bounds active overrides at 128 and restores them on expiry, quit and addon disable. Global world weather and other players are unaffected by the intended operation. Normal runtime cleanup must be verified on Paper.

The addon must implement cleanup for effects it creates. `unregister(owner)` withdraws its callable catalogue; plugin disable also removes registration. Script reload or revocation prevents future invocations but does not automatically undo effects created by Java code. In the weather example, earlier effects expire through the addon's own timer. This SDK is an owner-installed extension mechanism, not remote code installation, dependency resolution or a sandbox for Java plugins. External package distribution and typed addon events remain separate scope unless implemented and verified explicitly.

See the standalone example's README for the pinned Java 25/Paper build recipe, JAR installation, exact permission/grant commands and two-player acceptance checks. Compare actual installed signatures and addon versions before generating a script.


## Optional external integrations (0.0.28)

The built-in adapters expose a fixed set of invoking-player operations for Vault economy, Bukkit permissions and WorldGuard7. The owner installs compatible external plugins; scripts do not choose Java classes or member names. Optional dependencies are not bundled. Inspect installed provider health (provider, version and status) and validate against the connected server before using these calls. A script capability and an available provider are separate requirements.

`integration_available(provider, minimumVersion) -> boolean` requires `integration.inspect`. Providers are `"vault"`, `"permissions"` and `"worldguard"`. A numeric minimum has 1–4 dot-separated segments, each 1–9 digits; numeric prefixes compare with zero padding, and provider versions may have a nonnumeric suffix. Missing, incompatible or older providers return false. Invalid provider names/version syntax are errors. Availability never grants another capability. Vault's version is the Vault adapter plugin version, while provider health identifies the actual registered economy provider. Permissions uses the Bukkit API version. A compatible adapter version does not certify every economy implementation or all WorldGuard deployments.

| Call | Result | Capability |
| --- | --- | --- |
| `external_balance()` | integer minor-unit balance | `integration.economy.read` |
| `external_deposit(amount)` | boolean provider transaction success | `integration.economy.write` |
| `external_withdraw(amount)` | boolean provider transaction success | `integration.economy.write` |
| `external_permission(node)` | invoking player's Bukkit permission result | `integration.permission` |
| `external_can_build(location)` | WorldGuard7 build query result | `integration.worldguard.read` |

Economy values use exactly 100 minor units per major unit: 250 means 2.50. Balance conversion is exact and never rounds. The provider must advertise at least two fractional digits or unrestricted precision (`-1`); integer-only providers fail explicitly. A mutation accepts 1–100000000 minor units per call. The account is always the invoking player's. A provider transaction failure returns false; unavailable/incompatible APIs fail with bounded stable diagnostics. Provider exception text is not exposed.

`external_permission` delegates to `Player.hasPermission`, so the installed Bukkit permissions setup determines inheritance. Supply an explicit lowercase dotted node up to 128 characters without wildcards. `external_can_build` performs WorldGuard7's region `testBuild` query for the invoking player in their current world. It excludes WorldGuard session bypass and other protection plugins and neither performs nor cancels a block edit. Treat the result as that specific policy query, not universal permission to change a world.

### Guarded shop example and delivery scope

`examples/guarded-shop.aether` first checks Vault >=1.7, permissions >=1 and WorldGuard >=7.0, then checks `aetherscript.shop.buy` and WorldGuard build access at the player's location. The command permission is separately `aetherscript.example.guardedshop`. Quote mode reports a price of 250 minor units for four plain bread and the current balance. Buy mode re-reads balance and attempts withdrawal; it only attempts delivery after successful payment. The withdrawal result remains authoritative if balance changes after the read.

Install the external providers and the example, then run these console commands:

```text
ais permit guarded-shop integration.inspect,integration.economy.read,integration.economy.write,integration.permission,integration.worldguard.read,player.location.read,player.inventory.write
ais validate guarded-shop
ais reload guarded-shop
```

Grant the two explicit example permission nodes through the installed permissions system. Run `/ais run guarded-shop quote` and `/ais run guarded-shop buy`.

Item delivery uses the existing all-or-none `inventory_give` for the invoking player's storage inventory, excluding armor and offhand. It delivers four plain bread without changing unrelated metadata items. It is separate from the Vault transaction. A full inventory after payment triggers one compensating deposit; successful compensation reports the refund, while a rejected refund tells the player to contact the owner. There is no durable purchase journal, automatic refund retry or crash recovery in this example. A process failure after withdrawal can lose payment before delivery/refund; a process failure after delivery must not be treated as proof of failure and automatically refunded. This is not an atomic money-and-item exchange or an exactly-once purchase across crashes. `inventory_exchange` only combines two inventory descriptors and cannot make a Vault payment atomic.

Minimum-provider guards prevent attempts when required dependencies are absent or too old. A provider can still disappear or fail after a guard, so host calls retain their own failure checks. The configured minimums are example prerequisites, not a tested compatibility matrix. Record actual Vault, economy-provider, permission-provider, WorldGuard, WorldEdit and Paper versions when verifying interoperability.

The fixed adapter uses Vault's OfflinePlayer economy overloads and transaction result, Bukkit permission checks, and WorldGuard7's protection query. Source references: [Vault Economy API](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java) and [WorldGuard protection queries](https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/). External dependency installation, compatibility and their own authorization remain owner responsibilities.

## Reference priority

Use this current reference, discovered tool schemas and the bundled authoring contract for generation. The following full-guide appendix includes human tutorials, historical test records and explicitly planned work. Historical release examples do not add current capabilities.

State writes have an acceptance check immediately before commit. Cancellation rejects work not yet accepted; an accepted write may finish after logout, revocation, timeout or shutdown. Accepted and completed writes are not rolled back.

Test jobs retain at most 32 results globally and 8 per connection for up to 180 seconds; execution has a 120-second deadline. Captured message JSON is limited to 12 KiB and receipts to 6 KiB. `outputTruncated` or `receiptsTruncated` signals incomplete capture; truncated messages never count as a successful exact match. These capture limits do not undo effects already performed.

## Items, inventories and daily kits (0.0.21)

`item(material: text, amount: integer) -> item` constructs an immutable material/count descriptor. Use a lowercase material key such as `minecraft:bread`; an omitted namespace becomes `minecraft:`. Amounts range from 1 to 2304. Paper checks that the material is an available item. Items support equality, typed lists/map values and persistence, but cannot be set elements or map keys. Custom names, stable custom-item IDs and inventory menus are supported from 0.0.25; lore and enchantment authoring remain planned.

| Call | Result | Required capability |
| --- | --- | --- |
| `inventory_has(stack: item)` | `boolean`: sufficient matching items in storage slots | `player.inventory.read` |
| `inventory_give(stack: item)` | `boolean`: true when the entire amount was inserted | `player.inventory.write` |
| `inventory_remove(stack: item)` | `boolean`: true when the entire amount was removed | `player.inventory.write` |
| `kit_claim(key: text, items: list<item>, cooldownSeconds: integer)` | `text`: `claimed`, `cooldown` or `inventory_full` | `player.kit.claim` |

Plain descriptors match only plain material stacks without item metadata; custom descriptors match their exact feature-owned metadata. Unrelated metadata is preserved and excluded from checks, removal and merging. Inventory calls use the invoking player's storage slots, excluding armor and offhand. Give/remove return false without changing inventory when capacity/items are insufficient. These host calls require a player command and are unavailable in synchronous event handlers. Calls execute in source order; separate give, state and inventory calls do not form a transaction. Use `kit_claim` for the guarded cooldown-and-reward operation rather than combining a state flag with `inventory_give`.

A kit key matches `[a-z][a-z0-9_]{0,63}`. Bundles contain 1–64 descriptors and cooldowns range from 1 to 31536000 seconds. The cooldown is isolated by feature ID, player UUID and kit key, uses elapsed wall-clock time, and survives normal restart. A full inventory returns `inventory_full` without consuming the claim. `player.kit.claim` authorizes bundled delivery without separately requiring inventory write or script state permissions. Kit records use a separate namespace from `state_save`; source rollback does not restore cooldowns. Use a fixed kit key: letting players choose fresh keys would bypass a daily limit. Changing the feature ID or kit key creates a separate claim.

Copy `examples/daily-kit.aether` into `plugins/AetherScript/scripts/daily-kit.aether`. In the server console run:

```text
ais permit daily-kit player.kit.claim
ais validate daily-kit
ais reload daily-kit
```

Wait for `CAPABILITIES_SAVED` before validation/reload, and grant `aetherscript.example.dailykit` to the player. `/dailykit` grants 16 bread and 4 iron ingots and sends `Daily kit claimed.`. An immediate repeat, including after a server restart, sends `Your daily kit is on cooldown.`. With insufficient storage capacity it sends `Not enough inventory space; your daily kit remains available.`; free enough space and retry to receive the reward.

```aether
feature daily-kit version "1.0.0"
command dailykit permission "aetherscript.example.dailykit" sender player {
  let result = kit_claim("daily", [item("minecraft:bread", 16), item("minecraft:iron_ingot", 4)], 86400)
  if result == "claimed" {
    send "Daily kit claimed."
  } else {
    if result == "cooldown" {
      send "Your daily kit is on cooldown."
    } else {
      send "Not enough inventory space; your daily kit remains available."
    }
  }
}
```

The implementation reserves the durable cooldown before rechecking and delivering the inventory change. A failed delivery compensates the reservation. SQLite and the Minecraft player inventory cannot share a transaction: a process crash between reservation and delivery can consume the claim without delivering the reward. Operators must account for that recovery limitation; this is not an exactly-once grant across crashes. A backward wall-clock change may extend a cooldown and a forward change may end it sooner.

## Synchronous native events (0.0.20)

Native files support `on join`, `on quit`, `on block_break` and `on block_place`. A feature may contain only events, or an optional command followed by handlers. Each handler requires an explicit `permission`; `"*"` includes every player. Optional `bypass "permission.node"` skips holders of that node.

Every handler receives immutable `player_name`, `player_uuid` and `world` text values. The `world` field is a namespaced key such as `minecraft:overworld`. Block events additionally receive `block_type` text (the material name) and signed integer `x`, `y`, `z`. Event bodies support `let`, `send`, `if`/`else` and pure scalar expressions. Collection operations are rejected. Only block handlers support `cancel`. Host calls, delay, state operations, arguments, mutation, loops and user function calls are rejected in handlers. At most 16 handlers are allowed per feature; each evaluation has an 8192-step and 32-message cap.

Paper invokes handlers synchronously on its main thread. Block listeners use HIGHEST priority and skip events already cancelled by another plugin. AetherScript cancellation only sets the event's cancelled state; it never clears another cancellation. Active-revision subscriptions are replaced or removed during reload, rollback, disable and shutdown. Each handler infers `runtime.event.<id>`; `cancel` additionally requires `runtime.event.cancel`. The owner approves these with console `ais permit` or fresh hosted browser approval for join, quit, block break, block place and cancellation. Older grants keep their original scope. Sending also requires `runtime.message.send`. The hosted `aether_events_preview` tool can simulate a typed snapshot. Test actual gameplay and permissions on Paper.

### Starter world protection

Save this as `scripts/starter-protection.aether`:

```text
feature starter-protection version "1.0.0"
on block_break permission "*" bypass "aetherscript.protection.bypass" {
  if world == "minecraft:overworld" {
    send "Block breaking is protected here."
    cancel
  }
}
on block_place permission "*" bypass "aetherscript.protection.bypass" {
  if world == "minecraft:overworld" {
    send "Block placing is protected here."
    cancel
  }
}
```

Run these commands in the server console:

```text
ais permit starter-protection runtime.message.send,runtime.event.block_break,runtime.event.block_place,runtime.event.cancel
ais validate starter-protection
ais reload starter-protection
```

 Non-builders cannot break or place any material in `minecraft:overworld`. Other worlds are unaffected. Grant `aetherscript.protection.bypass` through your permissions system to builders who should edit there. Permission plugins determine wildcard/operator inheritance, so verify both builder and guest accounts. No extra script permission is needed by guests. The packaged example is opt-in: copying it into `scripts/` and reloading activates it.


## Records, modules and packages (0.0.23)

Use `record QuestProgress(stage: integer, title: text)` before functions and handlers. Construct with positional arguments, for example `QuestProgress(0, "Explorer")`, and read `progress.stage`. Records are immutable: construct a replacement to change fields. Names and field types are checked before activation. Declare referenced record types first; forward and recursive definitions are rejected. A feature allows at most 32 record declarations, with 1–16 fields each. Lists, maps, sets, function parameters/results and typed per-player state can contain records. Saved schema/type mismatches fail explicitly; changing a saved record schema requires an intentional migration or a new key.

A local package is `scripts/quests.aetherpack/`, containing `main.aether` and flat helper `.aether` files. Write whole-line `import "progress.aether"` directives at the start of a file. The entry then contains the feature header; helpers contain imports, record declarations and functions only. Imports compose one feature with a shared declaration namespace. Shared dependencies are included once; cycles, duplicate imports within one file and duplicate declarations fail. Functions and record types still follow declaration order in the composed source.

Use lowercase filenames matching `[a-z][a-z0-9-]*.aether`. Packages allow at most 32 reachable files and 65536 UTF-8 bytes in both source and composition. Unreachable files, subdirectories, links, non-source files and paths outside the package are rejected. The directory stem must match the feature ID. Do not install another source format for the same ID. Reload validates the whole package before replacing its active revision. Revisions retain the complete package for recovery and rollback. Diagnostics identify the originating filename, line and column.

The [quest example](https://github.com/MistDig/AetherScript/tree/v0.0.23/examples/quests.aetherpack) splits progress records, reward calculations and a second quest across four files. Copy the entire directory into the plugin scripts directory. Console approval and activation:

```text
ais permit quests state.own
ais validate quests
ais reload quests
```

As an authorized player, run `/ais run quests show`, then `/ais run quests advance`. A new player first sees `Explorer: stage 0`, `Reward preview: 0 bread`, and `Second quest: gather stone.` Advance saves stage 1 and changes the preview to 2 bread; subsequent show calls retain that progress. The reward is a calculation, not an item grant. Edit `second-quest.aether` and reload to change that quest independently.

Validate an installed release offline:

```text
java -cp AetherScript-MCP-0.0.23.jar dev.aetherscript.cli.Validate --package examples/quests.aetherpack
```

Package deployment is local-file administration. Hosted MCP authoring continues to accept one `.aether` source string; this release does not add multi-file apply tools, a remote package protocol, third-party dependency resolution or an addon loader.


## Menus and custom items (0.0.25)

`custom_item(id, material, name, amount)` constructs an immutable item descriptor. The local ID matches `[a-z][a-z0-9_]{0,63}` and is scoped by the feature ID. A name has 1–128 plain characters and amount is 1–2304. Material validation happens on Paper. Stable identity survives ordinary restart and reload; changing the feature ID or local ID creates another identity. Custom items support equality and typed persistence. Inventory matching includes their feature-owned ID, material and name; unrelated metadata is preserved. `kit_claim` currently accepts plain items only.

`menu_choose(title, items) -> integer` needs `player.menu` and an invoking player. Supply 1–54 item descriptors and a title of 1–128 plain characters. The host chooses the chest row count. Each displayed descriptor must fit one material stack. The invocation waits for a zero-based slot, or `-1` when the player closes the menu or another menu replaces it. Only ordinary left/right clicks on populated top slots select. All inventory movement while open is cancelled, including shift-clicks, dragging, double-clicks, hotbar swaps and offhand transfers. Preview items are never granted to the player.

Menus belong to an invocation and active revision. Quit, cancellation and revision retirement close owned sessions; authority is checked again before the selection continuation executes. Menus are transient and are not recovered after restart. Synchronous event handlers cannot open menus or perform inventory host calls.

`inventory_exchange(payment, reward) -> boolean` needs `player.inventory.exchange`. It removes the entire payment and inserts the entire reward in one storage-inventory operation, excluding armor and offhand. If payment or capacity is insufficient, it returns `false` with inventory unchanged. Plain payment matches plain items, so a named or otherwise modified emerald does not count as the sample price. This operation is atomic within the inventory; it does not form a transaction with SQLite, shared state or another plugin.

The shop uses an atomic exchange after selection. Closing or repeated movement attempts do not purchase anything. Install `examples/menu-shop.aether`, then approve and activate from the console:

```text
ais permit menu-shop player.menu,player.inventory.exchange
ais validate menu-shop
ais reload menu-shop
```

Grant the player `aetherscript.example.menushop` and run `/ais run menu-shop`. One plain emerald buys one Cloud token. A rejected exchange leaves payment and reward unchanged.

```aether
feature menu-shop version "1.0.0"
command menushop permission "aetherscript.example.menushop" sender player {
  let reward = custom_item("cloud_token", "minecraft:paper", "Cloud token", 1)
  let choice = menu_choose("Cloud shop: token costs 1 emerald", [reward])
  if choice == 0 {
    if inventory_exchange(item("minecraft:emerald", 1), reward) {
      send "Purchased one Cloud token."
    } else {
      send "You need one plain emerald and space for a Cloud token."
    }
  } else {
    send "Shop closed."
  }
}
```

## Hosted multi-file editing and event previews

With a 0.0.29 Paper plugin and compatible hosted relay, call `aether_scripts_read`, `aether_scripts_validate` and `aether_scripts_apply` with `format: "aetherpack"`. Supply `source` as a JSON string mapping relative `.aether` filenames to their contents. Include one feature entry and every imported module. See the four files in `examples/quests.aetherpack`. Read and keep the current editable hash, use `ABSENT` for creation, and re-read after applying. The active compiled hash and editable hash can differ after an owner edit. Owner-created package directories stay read-only through MCP. Text scripts continue to use the default format.

`aether_events_preview` takes a permitted feature ID plus an `event` with its `kind` and typed `fields`. Use the exact field names and types from the language reference. Results include simulated messages and cancellation intent for the active revision. A preview assumes the supplied snapshot reaches matching handlers. Verify real server permissions, other plugins, cancellation and world effects on Paper. A preview does not dispatch a live event.

Fresh hosted approval can include join, quit, block break, block placement and cancellation. Cancellation permission is separate from permission to receive an event. Existing grants do not gain permissions or tools automatically. Other event and world capabilities still require local owner approval.
