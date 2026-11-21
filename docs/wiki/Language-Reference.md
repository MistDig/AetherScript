[Home](https://github.com/MistDig/AetherScript/wiki/Home) Â· [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) Â· [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) Â· [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.21)

---

## Text scripts and structured definitions

AetherScript accepts **`.aether`, YAML and JSON** sources. They share permission checks, validation, saved revisions and recovery behavior. YAML/JSON retain the message-only model; native `.aether` scripts can also use the owner-approved command operations below.

For a single-file feature, save one file per feature as `scripts/<id>.aether`, `.yml`, `.yaml` or `.json`. Its filename and feature ID must match. Remove the other example format before installing a different one for the same ID.

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

## Your first text script

Save this as `scripts/hello-ai.aether`:

```text
feature hello-ai version "1.0.0"
command helloai permission "aetherscript.example.hello" sender any {
  let greeting = "Hello"
  let count = 2 + 3
  if count == 5 {
    send greeting + " from AetherScript!"
  } else {
    send "Unexpected count"
  }
}
```

```text
/ais validate hello-ai
/ais reload hello-ai
/ais run hello-ai
```

The result is `Hello from AetherScript!`. Values and conditions evaluate during preparation at validation/reload, and their selected messages are stored in the compiled feature. Every invocation then applies the feature's permission, sender and active-generation checks.

The `command` header registers a real Paper command name. Direct console invocation, replacement and restart are tested. Connected-player command-tree visibility remains unverified; `/ais run <id>` is also available.

## Command arguments in 0.0.7

Save this as `scripts/greet.aether`, validate it and reload it as an administrator.

```aether
feature greet version "1.0.0"
command greet permission "aetherscript.example.greet" sender any {
  arg name: text
  arg count: integer = 1
  arg excited: boolean = true
  if excited {
    send "Hello " + name + "!"
  } else {
    send "Hello " + name
  }
  if count > 1 {
    send "Repeat count: " + text(count)
  }
}
```

Run `/ais run greet MistDig` to receive `Hello MistDig!`. Run `/ais run greet Alex` to receive `Hello Alex!` from the same active script. For a name containing spaces, use `/ais run greet "Alex Smith"`.

Run `/ais run greet Alex 2 false` to receive `Hello Alex` and `Repeat count: 2`. An integer is a signed 64-bit decimal value. A boolean is `true` or `false`.

Declare arguments before other statements. Required arguments precede defaults. Types are `text`, `integer` and `boolean`. There are at most 16 positional arguments. Missing, extra and mistyped arguments produce an error and usage example before messages are delivered. An unclosed quote reports how to close it.

Argument-dependent calculations and conditions execute per invocation. Both branches are checked on validation. Legacy scripts using only `let`, `if` and `send` without arguments preserve their preparation behavior and semantic hashes. A `var`/`set`, loop or function declaration selects invocation-time evaluation even without arguments.

The following deliberately invalid script has a text default for an integer argument.

```aether-invalid
feature bad-default version "1.0.0"
command bad-default permission "aetherscript.example.bad" sender any {
  arg count: integer = "many"
  send text(count)
}
```

### Values and scope

| Form | Meaning |
| --- | --- |
| `let count = 2 + 3` | An immutable local integer. |
| `var count = 3` | A mutable local value, fresh for each invocation. |
| `set count = count - 1` | Update the nearest visible mutable binding with a matching type. |
| `let greeting = "Hello"` | An immutable local text value. |
| `let ready = count == 5` | An immutable local boolean. |
| `send greeting + "!"` | Append a plain message to the selected compiled output. |
| `if ready { ... } else { ... }` | Select a branch during preparation, or per invocation in commands declaring arguments or runtime control constructs. |
| `text(count)` | Explicitly convert an integer, boolean or text value to text. |

Locals are lexically scoped. An inner block may shadow an outer name. Duplicate declarations in the same block fail. An initializer can refer to earlier bindings. Names are ASCII letters/digits/underscores, beginning with a letter or underscore; reserved statement/value keywords cannot be local names.

### Operators

| Operators | Types and behavior |
| --- | --- |
| `+` | Add two integers or concatenate two text values. Use `text(...)` for mixed text/integer/boolean output. |
| `-`, `*`, `/`, `%` | Integer arithmetic. Division truncates toward zero and remainder keeps the dividend's sign. |
| `==`, `!=` | Equality between matching types; lists compare structurally. |
| `<`, `<=`, `>`, `>=` | Integer comparisons. |
| `!`, `&&`, `||` | Boolean negation, conjunction and disjunction. |
| `(...)` | Explicit grouping. |

Precedence from lowest to highest is `||`, `&&`, equality, integer comparison, addition/subtraction, multiplication/division/remainder, then unary operators. Integer operations reject signed 64-bit overflow. Evaluated division/remainder by zero fails. `&&` and `||` short-circuit evaluation, while both operands still receive static type checking.

Both branch bodies are parsed and type-checked before activation. An undefined name or mismatched type in an inactive branch rejects the source. Only the selected branch evaluates and contributes messages.

### Source and bounds

Statements end at a newline or at their closing-block boundary. `//` begins a comment. Text uses double quotes and supports escaped quotes, backslashes, newline, carriage return and tab. Emitted message text must satisfy the existing plain-text/control-character rules.

Source is strict UTF-8 with an optional leading BOM. Diagnostics identify the source line and column. The initial limits are 64 KiB source, 8,192 tokens, 2,048 expression nodes, 256 statements, nesting depth 32 and 8,192 preparation steps. Message-only prepared output must emit 1â€“32 nonempty plain messages, each at most 2,048 UTF-16 units. Host-backed commands may perform operations without emitting a message; emitted messages retain the output bounds.


## Mutable locals, loops and typed functions

var count = 3 declares an inferred mutable local. set count = count - 1 updates the nearest visible mutable binding with the same type. let, arguments and function parameters remain immutable. Locals support text, integer, boolean and homogeneous lists; state is fresh for every invocation and function call.

while condition { ... } requires a boolean. repeat count { ... } evaluates a nonnegative integer count once. break exits the nearest enclosing loop; continue skips to its next iteration. These keywords are valid only inside loops.

Declare fn name(parameter: integer): integer { ... } between the feature header and command. Parameters and results declare types explicitly: text, integer, boolean, `list<elementType>`, `map<K,V>`, `set<T>`, or the host-bound types described below. Call functions in expressions, for example text(double(3)). Every path of a non-unit function must return a matching value with `return expression`. Functions can call earlier declarations or themselves, but cannot call later declarations or capture command locals. Trailing parameters may declare typed constant defaults, for example fn double(value: integer = 2): integer { return value * 2 }. Required parameters precede defaults. Defaults cannot reference names or call user functions; immutable typed constants are shared safely across fresh frames. Limits are 32 functions, 16 parameters per function and 32 active function calls. Loops, expressions and nested calls share one 8192-step invocation budget and one 32-message output budget.

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

This example emits its lines immediately; loops do not delay server ticks. The owner-approved `delay` operation below provides bounded command continuations.

Test with {"id":"countdown","expectedMessages":["Count 3","Count 2","Count 1","Done"]}. Run /ais run countdown twice; both invocations start at 3.

PersistentVariables provides the JSON host backend; SQLiteVariables provides a separate transactional SQLite backend. Both remain core Java services. The Paper command runtime now binds the narrow location-storage operations below to SQLite; `var` itself remains invocation-local. Other hosts must select a backend and run storage calls on bounded workers; no automatic migration is performed. Native metadata registration and this runtime slice do not establish completion of the broader gameplay ecosystem.

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

## Owner-approved homes commands (current implementation)

This narrow native-script runtime supports player commands, persistent location snapshots and bounded waits. It does not complete the planned event, entity, model or general gameplay ecosystem. Fresh 0.0.17 browser approval selects all or selected native IDs, an execution capability set and optionally one test-player UUID. The AI can create, edit and activate native features within that grant. The execution set persists for installed features after AI disconnect. Console `ais permit` and `ais restrict` remain manual overrides that browser grants cannot exceed. Legacy personal-relay and older browser grants stay message-only until fresh approval. Install and approve this example locally, or ask an AI with a fresh native-capability browser grant to apply it.

Copy [`examples/homes.aether`](https://github.com/MistDig/AetherScript/blob/main/examples/homes.aether) to `plugins/AetherScript/scripts/homes.aether`. From the actual server console, run:

```text
ais permit homes player.location.read,state.own,scheduler.delay,player.teleport.self
ais capabilities homes
ais validate homes
ais reload homes
```

Wait for `CAPABILITIES_SAVED` before validating/reloading. Give the player `aetherscript.example.home` through your permission manager. In game, use `/home set main` to save the current location, then `/home go main` (or `/home`) to return after 20 server ticks. Tick duration depends on server progress. Names must match `[a-z][a-z0-9_]{0,63}`. Each feature/player UUID/name has separate storage; another player cannot read your home through these operations.

| Native call | Result and required capability |
| --- | --- |
| `player_location()` | Immutable `location` snapshot; `player.location.read`. |
| `state_get_location(name)` | `optional<location>`; `state.own`. |
| `state_put_location(name, destination)` | Durable write, returning `unit`; `state.own`. |
| `delay(ticks)` | Resume after 0â€“1200 server ticks, returning `unit`; `scheduler.delay`. |
| `teleport_self(destination)` | Teleport the invoking player only, returning `unit`; `player.teleport.self`. |

Requirements are inferred from host calls. Function signatures also accept `uuid`, `location`, `unit` and `optional<T>` in addition to existing types. These are immutable values, not live Bukkit objects. A location contains a world UUID, finite coordinates, yaw and pitch. There is no arbitrary platform-object access. A `unit` function can finish without a value. `is_some(saved)` tests an optional; `unwrap(saved)` returns its contained value and fails with `OPTIONAL_EMPTY` when empty. Check presence before unwrapping. Command arguments remain text, integer or boolean; no location command argument or general record constructor is supplied.

The homes example's `travel(destination: location): unit` helper sends a message, waits with `delay(20)`, teleports and sends confirmation. Player effects re-enter Paper's main thread, storage uses the bounded I/O worker, and waiting does not block Paper's tick thread. This is command execution only: there are no event handlers or independently persistent scheduled jobs. Host-backed sends stream as execution reaches them; a later failure does not undo earlier messages or completed writes/teleports.

Invocations are bounded to 120 seconds, 32 active globally, four per player and eight per feature, in addition to interpreter budgets. Player disconnect, shutdown, replacement/retirement of the active source revision, or removal of required execution capabilities retires pending work. Reconnecting does not resume it. Effects recheck the current revision, player session, capability ceiling and command permission. Teleport loads the target chunk asynchronously, then checks again before teleporting; missing worlds, invalid locations and cancelled teleports fail. Work already completed is not rolled back by cancellation.

Homes are stored in `plugins/AetherScript/player-state/variables.sqlite` and survive normal restarts and source revisions under the same feature ID. There is no automatic migration from the JSON backend or a script deletion API in this slice. Concurrent changes after an invocation reads a key can produce `STATE_CONFLICT`; inspect state before retrying. Storage failures are reported instead of silently losing a home. Back up the player-state directory with the server stopped; keep it separate from script revision backups.

`ais permit` replaces the feature's approved set (and always includes `runtime.message.send`); it is not additive. `ais restrict homes` restores message-only execution, retires incompatible active commands and cancels affected work. Manual console approval does not activate source: reload explicitly; an authorized AI apply activates through the durable pipeline. Policy lives in `plugins/AetherScript/script-capabilities.json` and survives restart. If policy storage is corrupt, execution falls back to a message-only ceiling until the owner repairs it. Fresh browser approval can supply execution capabilities for autonomous native apply; explicit console policy remains the ceiling. Native or player-only MCP tests use the owner-selected online player, who must hold the declared command permission. The AI cannot choose the actor. Tests cause real sends, state writes, waits and teleports. When `aether_scripts_test` returns `pending: true` and `testId`, poll `aether_scripts_test_status` with `{id,testId}` and report actual messages, errors and effects. Pure `sender any` message tests without an actor remain synthetic. Offline `aether_test` stays synthetic and rejects host execution with `HOST_CONTEXT_REQUIRED`.

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

## Items, inventories and daily kits (0.0.21)

`item(material: text, amount: integer) -> item` constructs an immutable material/count descriptor. Use a lowercase material key such as `minecraft:bread`; an omitted namespace becomes `minecraft:`. Amounts range from 1 to 2304. Paper checks that the material is an available item. Items support equality, typed lists/map values and persistence, but cannot be set elements or map keys. Custom names, lore, enchantments, custom item IDs and inventory menus remain planned.

| Call | Result | Required capability |
| --- | --- | --- |
| `inventory_has(stack: item)` | `boolean`: sufficient matching plain items in storage slots | `player.inventory.read` |
| `inventory_give(stack: item)` | `boolean`: true when the entire amount was inserted | `player.inventory.write` |
| `inventory_remove(stack: item)` | `boolean`: true when the entire amount was removed | `player.inventory.write` |
| `kit_claim(key: text, items: list<item>, cooldownSeconds: integer)` | `text`: `claimed`, `cooldown` or `inventory_full` | `player.kit.claim` |

Only plain material stacks without item metadata match; custom metadata is preserved and excluded from checks, removal and merging. Inventory calls use the invoking player's storage slots, excluding armor and offhand. Give/remove return false without changing inventory when capacity/items are insufficient. These host calls require a player command and are unavailable in synchronous event handlers. Calls execute in source order; separate give, state and inventory calls do not form a transaction. Use `kit_claim` for the guarded cooldown-and-reward operation rather than combining a state flag with `inventory_give`.

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

Paper invokes handlers synchronously on its main thread. Block listeners use HIGHEST priority and skip events already cancelled by another plugin. AetherScript cancellation only sets the event's cancelled state; it never clears another cancellation. Active-revision subscriptions are replaced or removed during reload, rollback, disable and shutdown. Each handler infers `runtime.event.<id>`; `cancel` additionally requires `runtime.event.cancel`. The owner must approve these locally with console `ais permit`; browser capability choices and legacy remote grants remain unchanged and do not authorize events. Sending also requires `runtime.message.send`. This release introduces no MCP tools. Command-test tools do not simulate event dispatch; test actual gameplay on Paper.

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

## YAML and JSON

Structured definitions continue to use schema version 1:

```yaml
schemaVersion: 1
id: hello-ai
version: "1.0.0"
requires:
  - runtime.message.send
commands:
  - name: helloai
    permission: aetherscript.example.hello
    sender: any
    actions:
      - type: message.send
        target: sender
        text: "Hello from AetherScript!"
```

| Field | Accepted meaning |
| --- | --- |
| `schemaVersion` | Integer `1`. |
| `id` | 1â€“64 characters, beginning with a lowercase letter followed by lowercase letters, digits or hyphens. Matches the filename. |
| `version` | Required nonempty label up to 64 characters, independent of saved revision numbers. |
| `requires` | Exactly `[runtime.message.send]`. The text frontend infers this requirement. |
| `commands` | Exactly one command definition. |
| `name` | Same character rules as feature ID; unique among active features. Used by the dispatcher and Paper native command metadata registration; client command-tree visibility remains unverified. |
| `permission` | Explicit dotted permission node up to 128 characters, with wildcard characters rejected. |
| `sender` | `any` or `player`. The latter restricts invocation to players. |
| `actions` | 1â€“32 message actions. |
| `type` | `message.send`. |
| `target` | `sender`, meaning the invoker. |
| `text` | Nonempty plain text up to 2,048 UTF-16 units, with control characters and legacy section-sign formatting rejected. |

Unknown fields/actions, duplicate keys, multiple documents, YAML tags, anchors and aliases are rejected. Quote string values. JSON requires quoted keys and valid commas. YAML/JSON values are literal definitions; the textual expression grammar belongs to `.aether` sources.

The engine caps active features at 128, tracked histories at 128 and scripts-directory entries at 256. Files must be regular files inside the owner-managed scripts directory.

### Make another feature

Copy an example to `scripts/welcome.aether` or `scripts/welcome.yml`. Set both feature ID and command name to `welcome`, choose the explicit permission `aetherscript.example.welcome`, and write your message. Validate, reload and invoke `/ais run welcome`.

[Working examples](https://github.com/MistDig/AetherScript/tree/v0.0.21/examples) Â· [Structured JSON Schema](https://github.com/MistDig/AetherScript/blob/v0.0.21/schema/feature-v1.schema.json) Â· [Grammar and architecture decision](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/ecosystem/language-decision.md) Â· [Capability matrix](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/ecosystem/capabilities.csv)

State writes have an acceptance check immediately before commit. Cancellation rejects work not yet accepted; an accepted write may finish after logout, revocation, timeout or shutdown. Accepted and completed writes are not rolled back.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) Â· AetherScript 0.0.21
