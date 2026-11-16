[Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.17)

---

## Text scripts and structured definitions

AetherScript accepts **`.aether`, YAML and JSON** sources. They share permission checks, validation, saved revisions and recovery behavior. YAML/JSON retain the message-only model; native `.aether` scripts can also use the owner-approved command operations below.

Save one file per feature as `scripts/<id>.aether`, `.yml`, `.yaml` or `.json`. Its filename and feature ID must match. Remove the other example format before installing a different one for the same ID.

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

Source is strict UTF-8 with an optional leading BOM. Diagnostics identify the source line and column. The initial limits are 64 KiB source, 8,192 tokens, 2,048 expression nodes, 256 statements, nesting depth 32 and 8,192 preparation steps. Message-only prepared output must emit 1–32 nonempty plain messages, each at most 2,048 UTF-16 units. Host-backed commands may perform operations without emitting a message; emitted messages retain the output bounds.


## Mutable locals, loops and typed functions

var count = 3 declares an inferred mutable local. set count = count - 1 updates the nearest visible mutable binding with the same type. let, arguments and function parameters remain immutable. Locals support text, integer, boolean and homogeneous lists; state is fresh for every invocation and function call.

while condition { ... } requires a boolean. repeat count { ... } evaluates a nonnegative integer count once. break exits the nearest enclosing loop; continue skips to its next iteration. These keywords are valid only inside loops.

Declare fn name(parameter: integer): integer { ... } between the feature header and command. Parameters and results declare types explicitly: text, integer, boolean, `list<elementType>`, or the host-bound types described below. Call functions in expressions, for example text(double(3)). Every path of a non-unit function must return a matching value with `return expression`. Functions can call earlier declarations or themselves, but cannot call later declarations or capture command locals. Trailing parameters may declare typed constant defaults, for example fn double(value: integer = 2): integer { return value * 2 }. Required parameters precede defaults. Defaults cannot reference names or call user functions; immutable scalar/list constants are shared safely across fresh frames. Limits are 32 functions, 16 parameters per function and 32 active function calls. Loops, expressions and nested calls share one 8192-step invocation budget and one 32-message output budget.

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

`values[0]` reads the first element; indices must be integers within bounds. `length(values)` returns an integer. `append(values, value)` returns a new list with a matching element type. Lists are immutable: use `var values = list<integer>()` followed by `set values = append(values, 1)` to replace a local list value. There is no indexed assignment or arbitrary host-array access. Lists contain at most 256 elements each and nest at most eight list types deep.

`==` and `!=` compare matching list types structurally, including nested elements. List concatenation with `+` and `text(listValue)` are rejected. Convert individual scalar elements with `text(value)` when building messages. Feature functions named `length` or `append` take precedence over these built-ins.

`for value in values { ... }` evaluates its list once and visits its elements in order. The loop variable is immutable and scoped to the body; it may shadow an outer name. `break` and `continue` target the nearest loop. Iteration and structural equality share the existing 8192-step invocation budget with expressions and function calls. Lists provide no maps, sets, records, closures or modules.

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
| `delay(ticks)` | Resume after 0–1200 server ticks, returning `unit`; `scheduler.delay`. |
| `teleport_self(destination)` | Teleport the invoking player only, returning `unit`; `player.teleport.self`. |

Requirements are inferred from host calls. Function signatures also accept `uuid`, `location`, `unit` and `optional<T>` in addition to existing types. These are immutable values, not live Bukkit objects. A location contains a world UUID, finite coordinates, yaw and pitch. There is no arbitrary platform-object access. A `unit` function can finish without a value. `is_some(saved)` tests an optional; `unwrap(saved)` returns its contained value and fails with `OPTIONAL_EMPTY` when empty. Check presence before unwrapping. Command arguments remain text, integer or boolean; no location command argument or general record constructor is supplied.

The homes example's `travel(destination: location): unit` helper sends a message, waits with `delay(20)`, teleports and sends confirmation. Player effects re-enter Paper's main thread, storage uses the bounded I/O worker, and waiting does not block Paper's tick thread. This is command execution only: there are no event handlers or independently persistent scheduled jobs. Host-backed sends stream as execution reaches them; a later failure does not undo earlier messages or completed writes/teleports.

Invocations are bounded to 120 seconds, 32 active globally, four per player and eight per feature, in addition to interpreter budgets. Player disconnect, shutdown, replacement/retirement of the active source revision, or removal of required execution capabilities retires pending work. Reconnecting does not resume it. Effects recheck the current revision, player session, capability ceiling and command permission. Teleport loads the target chunk asynchronously, then checks again before teleporting; missing worlds, invalid locations and cancelled teleports fail. Work already completed is not rolled back by cancellation.

Homes are stored in `plugins/AetherScript/player-state/variables.sqlite` and survive normal restarts and source revisions under the same feature ID. There is no automatic migration from the JSON backend or a script deletion API in this slice. Concurrent changes after an invocation reads a key can produce `STATE_CONFLICT`; inspect state before retrying. Storage failures are reported instead of silently losing a home. Back up the player-state directory with the server stopped; keep it separate from script revision backups.

`ais permit` replaces the feature's approved set (and always includes `runtime.message.send`); it is not additive. `ais restrict homes` restores message-only execution, retires incompatible active commands and cancels affected work. Manual console approval does not activate source: reload explicitly; an authorized AI apply activates through the durable pipeline. Policy lives in `plugins/AetherScript/script-capabilities.json` and survives restart. If policy storage is corrupt, execution falls back to a message-only ceiling until the owner repairs it. Fresh browser approval can supply execution capabilities for autonomous native apply; explicit console policy remains the ceiling. Native or player-only MCP tests use the owner-selected online player, who must hold the declared command permission. The AI cannot choose the actor. Tests cause real sends, state writes, waits and teleports. When `aether_scripts_test` returns `pending: true` and `testId`, poll `aether_scripts_test_status` with `{id,testId}` and report actual messages, errors and effects. Pure `sender any` message tests without an actor remain synthetic. Offline `aether_test` stays synthetic and rejects host execution with `HOST_CONTEXT_REQUIRED`.

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
| `id` | 1–64 characters, beginning with a lowercase letter followed by lowercase letters, digits or hyphens. Matches the filename. |
| `version` | Required nonempty label up to 64 characters, independent of saved revision numbers. |
| `requires` | Exactly `[runtime.message.send]`. The text frontend infers this requirement. |
| `commands` | Exactly one command definition. |
| `name` | Same character rules as feature ID; unique among active features. Used by the dispatcher and Paper native command metadata registration; client command-tree visibility remains unverified. |
| `permission` | Explicit dotted permission node up to 128 characters, with wildcard characters rejected. |
| `sender` | `any` or `player`. The latter restricts invocation to players. |
| `actions` | 1–32 message actions. |
| `type` | `message.send`. |
| `target` | `sender`, meaning the invoker. |
| `text` | Nonempty plain text up to 2,048 UTF-16 units, with control characters and legacy section-sign formatting rejected. |

Unknown fields/actions, duplicate keys, multiple documents, YAML tags, anchors and aliases are rejected. Quote string values. JSON requires quoted keys and valid commas. YAML/JSON values are literal definitions; the textual expression grammar belongs to `.aether` sources.

The engine caps active features at 128, tracked histories at 128 and scripts-directory entries at 256. Files must be regular files inside the owner-managed scripts directory.

### Make another feature

Copy an example to `scripts/welcome.aether` or `scripts/welcome.yml`. Set both feature ID and command name to `welcome`, choose the explicit permission `aetherscript.example.welcome`, and write your message. Validate, reload and invoke `/ais run welcome`.

[Working examples](https://github.com/MistDig/AetherScript/tree/v0.0.17/examples) · [Structured JSON Schema](https://github.com/MistDig/AetherScript/blob/v0.0.17/schema/feature-v1.schema.json) · [Grammar and architecture decision](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/ecosystem/language-decision.md) · [Capability matrix](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/ecosystem/capabilities.csv)

State writes have an acceptance check immediately before commit. Cancellation rejects work not yet accepted; an accepted write may finish after logout, revocation, timeout or shutdown. Accepted and completed writes are not rolled back.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.17
