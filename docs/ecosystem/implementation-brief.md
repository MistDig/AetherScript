# AetherScript: core language and addon ecosystem implementation brief

Prepared 9 November 2026. Intended reader: Codex working in the actual AetherScript repository.

## 1. Objective and instructions to the implementing agent

Evolve AetherScript into an approachable, extensible Minecraft scripting platform with the practical capabilities of Skript plus its major addon categories. Users should be able to build homes, shops, economies, quests, claims, minigames, custom abilities, menus, moderation, integrations, and network services through documented public scripting interfaces.

This document is a product specification and implementation backlog, not evidence that these features already exist. Work in incremental, tested vertical slices. Do not merely rewrite the README to claim support. Do not treat a generic Java bridge as completion of the beginner-friendly APIs.

Start by inspecting repository instructions, architecture, source, tests, roadmap, release policy, and supported platform pins. Reconcile this brief with the actual implementation. Preserve existing working behavior unless an explicit migration is provided. Record architectural choices and proceed with reasonable implementation decisions; ask only about genuinely blocking product choices.

Create a tracked feature matrix and dependency-ordered backlog. Implement the first incomplete foundational milestone, including working examples and appropriate tests, then continue through subsequent milestones as the working session permits. At each checkpoint state what works, what was tested, what remains, and the next concrete task. Never mark the entire project complete because one milestone works.

The user requested this document, not a repository modification or release in the current chat. A future implementation session must follow its own authorization and repository release instructions.

## 2. Starting point from the supplied README

The supplied README describes version 0.0.1: Java 25, targeting Paper 26.3 build 245, with strict YAML/JSON parsing, immutable typed message actions, permission-checked `/ais run`, explicit reload, SHA-256 revisions, registry generations, bounded background I/O, local JSONL audit, and a separate read-only offline MCP prototype.

It reports 42 automated engine/protocol tests and local startup/reload checks. These are README claims, not independently reproduced results. Connected-player continuity, Purpur, and named MCP client interoperability are explicitly untested.

Current documented limitations include no executable custom command aliases, expressions, variables, loops, events, inventories, automatic activation at startup, durable revision store, or crash recovery. Limits include one command per feature, 1–32 message actions, and 128 active features.

Preserve invalid-update protection, validation before activation, permission checks, bounded work, and honest compatibility reporting. Do not mistake the offline MCP validator for a live deployment service.

## 3. Scope and compatibility contract

Primary target: **capability parity**, using AetherScript's own language and APIs.

Separate future projects:

- Source compatibility: parsing existing `.sk` files with equivalent semantics.
- Binary addon compatibility: running existing Skript addon JARs against an implementation of the Skript APIs they depend on.
- Import tooling: translating a documented subset of Skript and flagging unsupported syntax precisely.

Do not claim either compatibility target merely because similar features exist. Existing addons generally extend Skript itself; installing them beside AetherScript does not automatically extend AetherScript.

“Any plugin” means broad server-side functionality with extension points. Client mods, arbitrary new client rendering, proxy behavior, and server internals may require other components. Publish explicit supported, optional, version-limited, and unsupported capabilities.

## 4. Architecture requirements

Use logical boundaries; module names below are suggestions, not a demand to discard the existing layout.

| Layer | Responsibility |
|---|---|
| Language frontend | Parsing, source spans, diagnostics, type checking, imports |
| Typed intermediate representation | Shared semantic model for script syntax and existing structured definitions |
| Runtime | Variables, functions, execution budgets, continuations, cancellation |
| Lifecycle manager | Prepare/activate/retire, ownership, dependency order, revision handling |
| Paper adapter | Commands, events, server objects, scheduler/thread constraints |
| Standard library | Collections, text, time, math, serialization, common gameplay helpers |
| Storage services | Persistence, transactions, migrations, durability policy |
| Extension SDK | Typed capability registration, services, serializers, documentation metadata |
| Optional integrations | Third-party plugins, packets, Discord, proxy, external storage |
| Developer tooling | Validation CLI, editor support, profiler, tests, examples |
| Optional AI gateway | Capability discovery and authoring; deployment only as a separately implemented feature |

Provide an ergonomic textual language for logic while preserving YAML/JSON for configurations and existing definitions. Specify grammar and semantics before multiplying syntax. All frontends should share validation and execution semantics where features overlap.

Every registered capability must declare: stable identifier, argument/return types, supported platform versions, execution context, required permissions/capabilities, resource ownership, error behavior, and documentation examples.

## 5. Core requirements

### CORE-01: Language

- Local, function, script, and persistent variables; explicit scope and mutability rules.
- Numbers, booleans, text, UUIDs, dates, durations, optional values, lists, maps, sets, and records.
- Typed references to players, entities, items, blocks, worlds, locations, vectors, inventories, and events.
- Arithmetic, comparison, boolean logic, interpolation, property access, collection indexing.
- If/else, bounded while, repeat, collection iteration, break, continue, return.
- Functions, arguments, defaults, return types, callbacks/closures with specified capture semantics.
- Modules, namespaces, imports, constants, configuration options, reusable packages.
- Defined missing-value behavior, conversion rules, equality, copy/reference semantics, numeric precision, and serialization.
- Structured error handling and asynchronous failure propagation.
- Source-mapped diagnostics and runtime stack traces.

Acceptance: reusable functions drive a persistent home system and a stateful quest without hard-coded feature handlers in Java. Invalid scripts fail before activation with useful source locations.

### CORE-02: Commands

Real registration, aliases, namespaces, descriptions, usage, subcommands, typed/optional arguments, defaults, suggestions, permission-aware completion, player/console sender restrictions, cooldowns, and collision reporting. Multiple commands per script. Reload/unload must update registration correctly.

Acceptance: `/home`, `/home set <name>`, and `/home delete <name>` work with suggestions; permissions and conflicts are tested; removed commands disappear after unload.

### CORE-03: Events

Cover player lifecycle, chat, commands, movement, interactions, combat, projectiles, entities, blocks, inventories, crafting, enchanting, trading, worlds, chunks, weather, and script lifecycle. Provide custom events and typed payloads.

Support priorities, cancellation where legal, cancellation filtering, mutable event fields where legal, and explicit synchronous/asynchronous semantics. Validate event-context expressions. Do not allow a delayed continuation to pretend it can still cancel an event that has already completed.

Acceptance: implement join rewards, damage modifiers, protected-block cancellation, and custom quest events. Retired handlers must never fire twice after reload.

### CORE-04: Minecraft API coverage

Players: teleport, health, hunger, XP, game mode, effects, flight, visibility, respawn, inventory, permissions.

Entities: spawn/remove, attributes, equipment, passengers, targets, pathfinding where exposed, projectiles, drops, persistent data.

Worlds/blocks: locations, vectors, geometry, ray tracing, block states/data, containers, signs, chunks, time, weather, borders, gamerules, world lifecycle where supported.

Items: material/type, amount, metadata, names/lore, enchantments, attributes, data components, custom persistent data, reliable cloning/comparison/serialization.

Presentation: Adventure-compatible rich text, click/hover components, translations, action bars, titles, boss bars, scoreboards, teams, tab lists, sounds, particles, display entities, resource-pack responses.

Gameplay: inventory operations, recipes, cooldowns, combat, loot/drop hooks, advancement-related APIs where supported, administrative actions with explicit authority.

Acceptance: no ordinary example requires arbitrary console-command strings where a supported typed API exists.

### CORE-05: Scheduling and execution contexts

Delay without blocking, repeat/one-shot tasks, handles, cancellation, timeouts, asynchronous I/O, bounded queues, and safe return to server execution contexts. Specify real-time versus tick-time semantics. Re-resolve player/entity references after delays and handle disappearance.

Paper support comes first. Folia requires a dedicated scheduler/entity-region ownership implementation and its own tests; do not advertise it from a successful Paper build.

### CORE-06: Persistence and configuration

Persistent variables, UUID-based player keys, script namespaces, configuration defaults/schema validation, supported-type serialization, SQLite baseline, optional external SQL, prepared statements, transactions, migrations, backups, and explicit flush/durability behavior.

Do not retain live Bukkit object references in durable state. Persist stable identities and supported value snapshots. A database transaction does not automatically make a remote economy provider plus inventory delivery atomic; define compensating actions and reconciliation for those workflows.

## 6. Addon capability parity: required first wave

The linked projects are reference implementations/ecosystem examples. The “AetherScript requirement” column is a proposed scope and may go beyond an individual addon's feature set. Links establish project purpose, not a guarantee of compatibility with the README's platform pins. Verify exact releases and APIs when implementing.

| ID / reference | AetherScript requirement | Placement | Acceptance example |
|---|---|---|---|
| ADD-01 [SkBee](https://github.com/ShaneBeee/SkBee) | Broad advanced Minecraft coverage: typed persistent data/components, optional version-specific NBT adapter, recipes, block data, structures, particles, rich components, world helpers, scoreboard/display-related APIs as supported | Standard Minecraft modules; isolated internal-version adapters | Custom tagged item retains identity after restart; recipe and structure operations work; effects clean up |
| ADD-02 [skript-reflect](https://github.com/SkriptLang/skript-reflect) | Trusted Java imports, constructors, methods, fields, overload resolution, conversions, interface callbacks, event integration, errors mapped to script source | Explicit trusted extension module | Call a third-party Java API and receive its callback; unsupported overload produces a useful error |
| ADD-03 [skript-gui](https://github.com/APickledWalrus/skript-gui) | Declarative menus, slot patterns, buttons/callbacks, pagination, per-player state, refresh, input flows, transfer protection | Standard GUI library | Shop resists shift-click, drag, number-key and double-click exploits; concurrent users remain isolated |
| ADD-04 [skript-yaml](https://github.com/Sashie/skript-yaml) | Typed YAML load/read/update/save, nested paths, lists, defaults, validation, scoped files; preserve comments only if explicitly supported | Standard configuration module | Edit quest configuration, save/reload, reject malformed updates without destroying valid state |
| ADD-05 [skript-db](https://github.com/btk5h/skript-db) | Async parameterized SQL, result mapping, pools, transactions, timeout/cancel, connection errors, migrations | Storage module | Concurrent purchase/transfer test preserves expected balance and stock; outage does not block ticks |
| ADD-06 [SkBriggy](https://github.com/ShaneBeee/SkBriggy) | Structured command trees, Minecraft-aware arguments, contextual suggestions, permission requirements | Core command adapter | Arena command tree has typed arguments and suggestions; reload removes stale nodes |
| ADD-07 [skript-placeholders](https://github.com/APickledWalrus/skript-placeholders) | Resolve external placeholders and register script-provided placeholders; context, caching, lifecycle, threading rules | Optional PlaceholderAPI adapter | Another plugin resolves a script-defined quest-progress placeholder; unload unregisters it |
| ADD-08 [DiSky](https://github.com/DiSkyOrg/DiSky) | Discord bot integration: events, messages, embeds, slash commands, components, modals, interactions; credentials, rate limits, reconnect and lifecycle | Optional Discord module | Test bot handles an interaction and returns a response without blocking or leaking tokens |
| ADD-09 [skript-worldguard](https://github.com/SkriptLang/skript-worldguard) | Region lookup, containment, membership/ownership, priorities, applicable flag queries and supported changes, lifecycle and persistence | Optional WorldGuard adapter | Protection respects overlapping regions and effective flags, not only a raw region list |
| ADD-10 [Lusk](https://github.com/JakeGBLP/Lusk) | Audit its documented miscellaneous features against the core coverage matrix; close useful missing entity/player/event/property gaps | Core or small standard modules | Each selected gap has a public binding, example and behavior test; no vague “Lusk support” checkbox |

Reflection is an escape hatch, not a sandbox. Native extensions and unrestricted Java execution have host-process authority. Restricted scripts must not acquire reflection privileges indirectly through shared objects or helper libraries.

### ADD-11: skript-variables — visual variable administration

Reference: [skript-variables project description](https://modrinth.com/plugin/skript-variables). This is distinct from language variables and persistence: it provides a web interface for inspecting and editing server variables. The project describes hierarchical browsing, typed editors, player-name/UUID search, regex and type filters, bulk changes, rename/clone/delete, staged browser edits, explicit server-side apply, single-use apply codes, and a cancellable pre-apply event. Its snapshot includes unsaved in-memory variables. These are upstream-described capabilities, not independently tested behavior or proof of AetherScript compatibility.

Build an equivalent **AetherScript variable explorer and editor** as a first-class administration tool. Do not mark this complete merely because persistent variables or a text inspection command exist.

#### Required browsing and editing capabilities

- Browse script namespaces and nested variable paths as expandable folders; show keys, types, values and subtree counts.
- Search by key, value and known player name/UUID; offer bounded regex search, type filters, changed-only filters and sorting.
- Provide typed editors for text, numbers, booleans, dates, durations, locations, vectors, item/block data, rich text, nested collections and records.
- Validate worlds, coordinates, numeric ranges and type constraints. Vector tools should include length and normalization with defined zero-vector behavior.
- Permit addon-defined editors/codecs for extra types such as NBT, bounds and custom structs. Unsupported types remain inspectable/read-only rather than being silently converted to strings.
- Create, update, rename, clone and delete individual variables; support scoped bulk updates and subtree operations with an affected-key preview.
- Read authoritative live persistent-variable state, including changes not yet flushed to disk. State clearly which transient/local execution scopes are excluded; this is not automatically a debugger for stack-local variables.
- Paginate or virtualize large datasets and bound snapshot, search and bulk-edit work.

#### Staged apply workflow

Suggested commands are design proposals, not existing syntax: `/ais variables editor` creates a session; `/ais variables apply <session> <code>` applies a reviewed change set.

1. Authorize access and capture a consistent, versioned snapshot of the permitted namespaces.
2. Open the browser editor; keep edits staged until explicitly applied.
3. Show additions, edits, deletions, renames and affected keys before apply.
4. Validate values and recheck authorization at apply time. Fire a cancellable pre-apply hook with the proposed diff.
5. Compare expected per-key revisions with live state. Reject conflicting edits rather than overwriting gameplay changes made since the snapshot. Renames check both source and destination; subtree changes must detect newly added keys or operate on an explicitly frozen key set.
6. Commit through the normal storage/runtime service, with a documented atomicity boundary. Do not bypass change listeners, type rules or persistence behavior.
7. Report success/failure and durability status, consume the apply authorization on success, and audit the operation. Repeated requests must not apply twice; define safe retry behavior after an uncertain response.

#### AetherScript-specific additions beyond the upstream description

- Separate browse and edit permissions, namespace scopes, expiring sessions, single-use apply authorization and secret-value exclusion/redaction.
- Choose and document local/self-hosted versus externally hosted editor transport before implementing it. Do not automatically upload all server state to an external website; export only the explicitly authorized scope.
- Authenticate the actual transport, keep session credentials out of routine logs, and specify session cleanup after logout, expiry, reload and restart.
- Record old/new values where appropriate for audit and recovery, while excluding secrets. A restore is a new conflict-checked operation, not a blind overwrite of newer gameplay state.
- Return exact failed keys or reject the whole atomic batch; never report partial success as complete success.
- Expose editor metadata through the extension SDK so new serialized types can participate without modifying the editor core.

#### Acceptance tests and milestone placement

- Browse a nested player-data tree, locate it by known player name, edit a location and clone a record with its types preserved.
- Confirm browser edits leave live state unchanged until apply, and cancellation leaves every targeted value unchanged.
- Change a balance through gameplay after opening the editor; stale apply must report a conflict and preserve the newer balance.
- Test unauthorized/expired sessions, replay, rename collisions, invalid types, subtree conflicts, unsupported addon types and absent providers.
- Confirm unflushed persistent values appear; verify successful changes survive restart according to the declared durability policy.
- Verify large datasets remain responsive without blocking server ticks; test failed persistence and lost apply responses.
- M2 defines versioned variable access and change hooks. M3 delivers the explorer and typed editor for core types. M4 adds SDK editor extensions. M6 verifies recovery, scale and transport behavior. Authorization, conflict checks and safe apply are required at first write-capable release.

## 7. Extended ecosystem coverage backlog

These are required capability categories for the broader roadmap. Names are discovery leads, not verified support claims or a statement that every project is currently maintained. Before implementation locate the original repository/docs, record the exact version, verify its capabilities and platform support, and map overlap with the core. Do not blindly install historical addons.

| ID | Category / addon leads | Proposed capability and acceptance gate |
|---|---|---|
| EXT-01 | WorldEdit/FAWE; skript-worldedit | Selections, edit sessions, schematics, clipboard transforms, bounded operations, undo where supported. Paste/reset an arena and recover from cancellation. Verify WorldEdit and FAWE independently. |
| EXT-02 | LuckPerms; skript-luckperms | Users, groups, nodes, contexts, temporary permissions, metadata, async load/save. A temporary contextual rank expires correctly. |
| EXT-03 | Vault/economy services | Provider abstraction, balance queries, deposit/withdraw responses, errors, optional permission/chat services. A shop handles insufficient funds and provider failure. |
| EXT-04 | skJSON and HTTP utility addons | Typed JSON trees, parse/serialize/path edits, HTTP methods/headers/body/status, bounded responses, timeouts, cancellation, retry policy. Remote failure produces a script-visible error. |
| EXT-05 | PacketEventsSK, packet addons, ProtocolLib/PacketEvents | Typed packet listeners, sending, cancellation/modification, per-player virtual effects, protocol-version mapping. Fake entity visible to one player cleans up on disconnect/reload. Choose and test an actual backend. |
| EXT-06 | SkRedis and network messaging addons | Pub/sub, scoped keys, TTL, connection lifecycle, explicit ordering/delivery guarantees, idempotency. Reconnection does not apply a duplicate reward. |
| EXT-07 | Skungee, BungeeSK, proxy integration leads | Velocity-first or explicitly chosen proxy companion, player routing, cross-server events, authenticated messages. Verify a two-server transfer; do not claim proxy support from a Paper-only plugin. |
| EXT-08 | Citizens/Skonic and NPC addons | NPC create/remove, traits, appearance where API permits, interaction, navigation, persistence. Quest NPC survives restart without duplication. |
| EXT-09 | ItemsAdder, Oraxen, other custom-content providers | Namespaced custom item/block/furniture lookup, creation and events via provider APIs. Missing provider gives a clear dependency error; resource-pack requirements are documented. |
| EXT-10 | MythicMobs, ModelEngine, BetterModelSk and model integrations | Custom mobs, skill hooks, models, animations and interactions through supported provider APIs. Spawn a scripted boss and clean up its resources. |
| EXT-11 | DisplayEntityUtils, animated-skript | Display transforms, interpolation, groups, animation timelines and viewer visibility. Animated menu/hologram has bounded updates and no orphan entities. |
| EXT-12 | skript-particle, skDragon-style effects | Shapes, paths, vector math, reusable effects and per-viewer budgets. Cancellation removes scheduled emitters. |
| EXT-13 | skLambda, oopsk, ClassySK, LibImporter | Evaluate callbacks, records/classes, reusable libraries and imports against the language design; native language support is preferred over duplicate dialects. Reusable quest library loads with dependency checks. |
| EXT-14 | SkQuery, Skellett, skRayFall, TuSKe | Historical broad-addon gap audit. Categorize each needed feature as core, integration, deprecated, or unsupported. Preserve useful use cases rather than recreating obsolete internals. |
| EXT-15 | skUtilities, skent, SkLogs | Scoped files/directories, text and encoding utilities, structured logging, rotation, export. Path traversal is rejected and logs do not grow without policy. |
| EXT-16 | MongoSK and alternate data stores | Optional document-store provider only where demanded; typed operations, indexes, timeouts and migrations. Do not make it a dependency of basic scripts. |
| EXT-17 | SkNMS and internal-server bridges | Version-isolated trusted internals access for features unavailable through public APIs. Unsupported versions fail closed with a precise diagnostic. |
| EXT-18 | SlimeLink / instanced world providers | Optional world-template/instance adapters, ownership, unload/delete boundaries. Concurrent minigame instances remain isolated. |
| EXT-19 | TesttySK and testing tools | Script assertions, deterministic clock/random sources, event fixtures, integration harness, profiler. Example packages test through public APIs. |

Acceptance of this section requires a researched coverage inventory, not a promise that every named addon is fully cloned. Publish implemented subsets and remaining gaps. The ecosystem changes; maintain the inventory rather than declaring a permanently exhaustive list.

## 8. Extension SDK and package design

- Register types, parsers, serializers, functions, expressions, conditions, actions, sections and events through stable public interfaces.
- Prefer a single typed callable model with ergonomic syntax rather than independent implementations of equivalent operations.
- Provide custom event payloads, service discovery, dependency injection and lifecycle callbacks.
- Publish semantic API versions, deprecation policy and migration examples.
- Package manifest: identifier, version, engine/API range, dependencies, optional dependencies, entrypoints, capabilities and configuration schema.
- Detect cycles, conflicting identifiers and unsupported dependency versions before activation.
- Load only installed/selected integrations; absent optional plugins must not break the core.
- Generate reference documentation and editor completion metadata from registrations.
- Include a small third-party sample addon built against only the public SDK.
- Define classloader/dependency isolation and cleanup; do not promise arbitrary Java-plugin hot unloading.

Gate: an external addon registers a new type, event, function and serializer without modifying AetherScript internals. Disable/unload behavior is verified.

## 9. Reload, state and durability

Every listener, command, task, GUI session, placeholder, network subscription and managed entity must have a clear owner and cleanup policy. Shared or intentionally persistent resources require explicit transfer/retention semantics.

Use prepare/validate/compile before activation. Define a commit boundary for registry and resource replacement. Old continuations carry a revision token and cannot silently mutate replacement state. Document whether in-flight handlers finish, drain or cancel.

Specify state migration, dependency reload order, startup activation, durable revision records, last-known-good restoration, crash recovery and audit rotation. Test failures before activation, during registration, during cleanup and after persistence acknowledgement.

Registry rollback does not reverse messages, teleports, Discord requests, payments or world edits. Irreversible effects need explicit workflows or compensating operations. Do not call an entire gameplay deployment transactional without evidence.

Gate: 100 repeated reloads with connected players, active timers and open menus show no duplicate callbacks or monotonic growth of owned resources. Crash/restart tests match documented durability claims.

## 10. Performance, authority and operational controls

- Budget interpreted execution, loops, recursion, collection size and expensive world operations.
- Bound background queues, pending requests, particles, entity creation and scheduled tasks.
- Attribute timings, failures and resource counts to script and source location.
- Batch expensive edits and avoid scanning all entities every tick by default.
- Provide slow-handler diagnostics, cancellation and circuit breakers.
- Keep blocking storage/network/file work off the game thread; respect each API's thread contract.
- Distinguish player invocation permissions from script execution capabilities.
- Scope files and data; use secret references, redaction and capability-controlled external services.
- Expose trusted Java access explicitly; cooperative interpreter budgets cannot forcibly make arbitrary native Java calls safe.
- Publish benchmark hardware, platform, player/load conditions and measured results. No “zero lag” claims.

## 11. Ease-of-use requirements

Provide beginner-focused defaults and high-level building blocks: menus, cooldowns, paginated lists, region checks, conversations, quests, timers, arena state machines and provider-backed economy operations. These should compose from public APIs.

Ship searchable reference docs, short runnable examples, editor highlighting/completion, hover help, formatter, linter, language-server diagnostics, a validation CLI, runtime inspection and source-linked profiling.

Provide a deployment checklist generated from actual script dependencies: engine version, platform requirements, capabilities and optional plugins. A user should not learn about a missing integration only after a live event fires.

AI/MCP work follows the runtime foundations: machine-readable capability schemas, exact installed versions, validation diagnostics and examples. Live-server connection, credentials, deployment and rollback are separate deliverables. Do not expand the existing offline prototype's claims prematurely.

## 12. Milestones and completion gates

These identifiers are dependency phases, not assigned release versions. Reconcile them with the repository's existing roadmap and release policy.

| Phase | Deliverables | Gate |
|---|---|---|
| M0: audit | Source audit, grammar decision, architecture note, platform matrix, feature inventory | Every baseline claim linked to source/test or marked unverified |
| M1: language/runtime | Types, variables, expressions, branches, loops, functions, diagnostics, budgets, initial SDK contracts | Pure-language behavior tests and invalid-input diagnostics pass |
| M2: first real plugins | Registered commands, events, scheduling, player/item/location APIs, persistence | Homes/warps, kits and join rewards run and survive documented reload/restart paths |
| M3: rich gameplay | GUI, components/data, recipes, effects, combat/world APIs, standard configuration/JSON, ADD-11 variable explorer/editor | Shop, custom ability and quest examples run; inventory exploit cases and staged variable-edit conflicts tested |
| M4: integrations/SDK | Public SDK sample, placeholders, permissions/economy, WorldGuard/WorldEdit adapters | External addon compiles against SDK; installed/missing/incompatible provider cases pass |
| M5: advanced ecosystem | Trusted Java bridge, packets, HTTP/Discord, Redis/proxy, NPC/content adapters in researched priority order | Each claimed adapter has a version matrix and actual integration evidence |
| M6: production/tooling | Durable lifecycle, crash tests, profiler, editor tooling, package workflows, migration guide | Stress/reload tests and all flagship examples pass on supported configurations |

Implement ownership, thread rules, permissions and relevant failure tests within every milestone. M6 consolidates production evidence; it is not permission to defer correctness until the end. Revisit SDK contracts early so integrations do not require a language rewrite.

## 13. Required flagship examples

1. Homes/warps/teleport requests: arguments, persistent locations, delay/cancel, permissions.
2. Kits: item definitions, persistent cooldowns, permission-aware completion.
3. Economy shop: GUI state, concurrent purchases, provider failures, inventory delivery reconciliation.
4. Claims: spatial queries, event cancellation, effective WorldGuard rules where integrated.
5. Quests/NPCs: reusable records, custom events, persisted progress, optional NPC provider.
6. Arena minigame: teams, countdowns, cleanup, disconnect/rejoin, arena reset.
7. Custom boss: combat hooks, effects, tasks, optional custom-mob/model provider.
8. Auction house: SQL transactions, concurrent bids, expiration, crash/restart recovery.
9. Discord bridge: interactions, asynchronous failure handling, secrets and lifecycle.
10. Cross-server feature: proxy companion and messaging with duplicate/reconnect handling.
11. Variable administration: find player data through the browser, stage typed edits, preview the diff, reject stale changes, apply an authorized batch and verify persistence.

Use public scripting APIs throughout. Report any internal Java special case as an API gap. Never substitute a mock-only test for a claimed Minecraft/client integration test.

## 14. Tracking format and definition of done

Create one row per concrete capability, not just one per addon:

```text
ID | domain | upstream reference/version | user story | Aether API/syntax
| core/optional/trusted | dependencies | thread context | permissions
| lifecycle ownership | persistence | status | example | test/evidence
| supported versions | limitations | next task
```

Allowed statuses: unresearched, specified, implementing, implemented-unverified, verified, intentionally unsupported, deprecated. A documentation stub is not implementation; a unit test is not live integration evidence.

A feature is done only when it has a working public interface, documented semantics, example, relevant positive/negative tests, lifecycle/thread behavior, dependency handling, and truthful version support. Update changelog and roadmap according to repository policy. Do not overwrite existing published assets or tags.

Track compatibility separately for engine, Paper, Java, each integration provider, and client-visible behavior. Do not infer Purpur or Folia support from Paper tests.

## 15. First implementation assignment for Codex

1. Read this brief and repository instructions. Inspect actual code and tests before changing architecture.
2. Write a concise gap report and the capability matrix above, preserving the README's distinction between implemented and planned features.
3. Choose and document the smallest extensible language/runtime design compatible with existing typed actions.
4. Implement the first incomplete foundational slice. A useful initial target is variables + expressions + conditions + source diagnostics, followed by real custom-command registration and a persistent `/home` example when dependencies are ready.
5. Keep current message scripts and invalid-update preservation working, or provide an explicit tested migration.
6. Run appropriate checks and update examples/docs. Do not claim unexecuted tests passed.
7. Continue in dependency order; leave a precise checkpoint if the session ends. Do not replace the requested engineering work with another broad plan.

## 16. Research sources and verification notes

Primary sources reviewed for this brief:

- [skript-variables project description](https://modrinth.com/plugin/skript-variables) — visual variable editing and staged server-apply workflow; added after the initial brief.

- [Skript official reference](https://docs.skriptlang.org/) — baseline syntax families and behavior reference.
- [Skript source repository](https://github.com/SkriptLang/Skript) — project/API reference.
- [Paper scheduling documentation](https://docs.papermc.io/paper/dev/scheduler/) — execution-context constraints.
- [SkBee](https://github.com/ShaneBeee/SkBee), [skript-reflect](https://github.com/SkriptLang/skript-reflect), [skript-gui](https://github.com/APickledWalrus/skript-gui).
- [skript-yaml](https://github.com/Sashie/skript-yaml), [skript-db](https://github.com/btk5h/skript-db), [SkBriggy](https://github.com/ShaneBeee/SkBriggy).
- [skript-placeholders](https://github.com/APickledWalrus/skript-placeholders), [DiSky](https://github.com/DiSkyOrg/DiSky), [skript-worldguard](https://github.com/SkriptLang/skript-worldguard), [Lusk](https://github.com/JakeGBLP/Lusk).

This research establishes an implementation direction and selected addon categories. It is not a release-by-release audit of every syntax, a benchmark, or a tested compatibility matrix. Extended-backlog addon names require primary-source verification during M0 and before their implementation. Inspect dependency licenses before reusing or distributing code; this brief requests capability reimplementation and supported API integrations, not wholesale copying of upstream implementations.
