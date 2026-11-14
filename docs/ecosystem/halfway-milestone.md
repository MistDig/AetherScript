# Halfway milestone: M1–M3 acceptance checkpoint

Recorded 14 November 2026 against the inspected 0.0.10 source baseline. This is the current checkpoint for the owner's next goal: complete the first three implementation phases in the [exact supplied brief](implementation-brief.md). M0 is the audit prerequisite; M4–M6 remain later work. “Halfway” means three of the six dependency phases M1–M6 have passed their gates. It does **not** mean 50% of capabilities, engineering effort, risk, addon parity, or production readiness. Phases differ substantially in size. No halfway completion is claimed here.

The existing [capability CSV](capabilities.csv), [gap report](gap-report.md), [language decision](language-decision.md), and [backlog](backlog.md) are dated inventories. Their historical statuses and evidence remain intact. This checkpoint adds current acceptance criteria rather than marking those rows verified in bulk. Status words follow brief section 14. Evidence below is inspected source or previously recorded execution; this documentation change ran no Java or live-server tests.

## Current evidence and limits

| Concrete capability | Status and evidence | Limit / next task |
| --- | --- | --- |
| Strict YAML/JSON static message definitions | verified for recorded tests: `core/.../FeatureParser.java`, `EngineTest.java`, [0.0.9 XML](../evidence/0.0.9/TEST-dev.aetherscript.core.EngineTest.xml) | Schema v1 still has one command and sender-only messages; it does not represent general gameplay programs |
| Text locals, integer/text/boolean expressions, if/else, source locations and bounds | verified for recorded corpus: `TextFrontend.java`, `TextFrontendTest.java`, [0.0.9 XML](../evidence/0.0.9/TEST-dev.aetherscript.core.TextFrontendTest.xml) | Immutable lexical locals, three scalar types; no full variable/type system |
| Positional typed invocation arguments, defaults and fresh evaluation | verified for recorded corpus: `RuntimeArgumentsTest.java`, [0.0.9 XML](../evidence/0.0.9/TEST-dev.aetherscript.core.RuntimeArgumentsTest.xml) | RuntimeProgram currently selected by arg declarations; no-arg programs lower to static messages |
| Validation before replacement, revisions, startup recovery and local rollback | verified for recorded corpus: `FeatureManager`, `RevisionStore`, `RecoveryTest`, `RemoteIntentRecoveryTest`; [test history](../testing.md) | Persistent source revisions are not persistent gameplay variables; no claim of atomic reversal of effects |
| Permissions and stale-generation checks before message effects | verified for recorded runtime corpus: `Registry`, `RuntimeArgumentsTest` | New effect adapters and continuations each need equivalent authority/ownership checks |
| Paper console lifecycle and 100 changed reloads | verified only for the named historical runs in [testing](../testing.md), including [0.0.7 Paper recovery](../evidence/0.0.7/paper-recovery.json) | No connected-player GUI/timer/resource-growth evidence; no new run for this checkpoint |
| Hosted scoped authoring and transport | existing recorded implementation/evidence in [testing](../testing.md) | This does not supply gameplay APIs, variable-editor transport, or actual-client certification |
| Loops, reusable functions, collections/records, persistent script variables | specified; absent from inspected baseline frontend statement/type set | Implement bounded runtime semantics and tests before M1 completion |
| Script command registration, gameplay events/scheduler, typed Minecraft values and SQLite variables | specified; no public bindings in inspected baseline | `/ais run` plus command-name metadata does not meet M2 |
| GUI, rich gameplay, configuration/JSON public APIs and ADD-11 browser editor | specified; no implemented public baseline interfaces found | Source parsing, text messages and persistent source files do not meet M3 |

Source paths above abbreviate `core/src/main/java/dev/aetherscript/core/` or `core/src/test/java/dev/aetherscript/core/`. Recorded 0.0.9 evidence reports 190 Java tests; 0.0.10 is a documentation audit. These counts are historical, not newly reproduced results. Supported Paper evidence is pinned to 26.3 build 245 / Java 25. Purpur, Folia, provider versions, connected-client behavior and load claims need separate evidence.

## 0.0.11 foundation slice — 15 November 2026

The baseline table above describes inspected 0.0.10. The new slice implements mutable invocation locals, bounded while/repeat, nearest-loop break/continue, typed scalar functions, returns and bounded self-recursion. Frames are isolated; loops/calls share step and output budgets. Functions must precede use; collection types, function defaults, closures and modules remain open.

Paper now registers real feature command names, enforces the same invocation permission/sender rules, rejects foreign collisions, restores failed replacements and removes owned retired labels. Console integration checks cover two argument lists, quoted values, rename/removal and restart. Connected-player suggestions/tree refresh, aliases, multiple command trees and subcommands are not certified.

`PersistentVariables` is a typed host API with UUID/feature keys, per-key revisions, compare-and-swap batches and checksummed atomic JSON storage. Core tests cover conflicts, restart, corruption, interruption and bounds. It has no script bindings, SQLite, deletions, change hooks or browser editor. It is a foundation rather than M2-D completion.

Exact verification is recorded in [0.0.11 evidence](../evidence/0.0.11/) and [testing](../testing.md). M1, M2 and M3 gates remain open. Next dependency work is the broader value/callable model, then typed gameplay/event/scheduler and persistent script bindings before homes/kits/join rewards.

## 0.0.12 list/default/SQLite slice — 15 November 2026

Homogeneous immutable lists now support literals, typed empty constructors, indexing, length/append, structural equality and bounded for iteration. Functions accept list parameters/results and trailing typed constant defaults. List length/depth and shared execution budgets are checked. Legacy semantic hashes are compared with the published 0.0.11 artifact. Maps, sets, user-defined records, closures, modules and the broader public callable/value model remain open.

A separate SQLite host backend supplies transactional revision CAS and strict typed/schema validation. Real provider tests and both packaged JARs verify restart/CAS behavior. It has no script bindings, automatic migration, deletion/change hooks or variable editor. M2-D remains open. All M1–M3 phase gates remain open; this is a verified subset, not halfway completion. [Evidence](../evidence/0.0.12/)

## Dependency order and measurable gates

A phase is accepted only after every relevant gate below has implementation, public syntax/API documentation, runnable example, positive/negative tests and lifecycle/thread/version evidence. Partial slices remain partial; a passing unit suite cannot stand in for Paper/client acceptance.

### M1 — language and bounded runtime

Depends on the M0 audit and retention of the structured-message compatibility contract.

- M1-A: Specify scope/mutability, primitive and collection/record value semantics, missing values, conversions, equality, numeric limits, copying/reference rules and serialization boundaries. Public tests exercise those semantics, invalid input and source locations.
- M1-B: Implement invocation-local state, branches, bounded while/repeat/collection iteration, break/continue/return, typed reusable functions and arguments/defaults/returns. Define recursion/capture behavior; explicitly track unsupported closures/modules rather than implying the complete CORE-01 surface. All paths type-check before activation. Loops and calls share step/depth/output budgets with actionable failures.
- M1-C: Expose initial typed capability contracts: stable IDs, argument/return types, platform scope, thread context, permission/capability requirements, resource owner, error semantics and examples. Public callable semantics must be reusable by subsequent adapters; initial contracts are not the M4 external-addon completion claim.
- M1-D: Pure-language examples run via the public parser/runtime and packaged validator/MCP; invalid syntax/type/budget failures preserve the active revision and emit no partial buffered messages. Existing YAML/JSON/static-text hashes and behavior remain compatible or receive an explicit tested migration. Hashes include loop/function semantics, not comments/positions.

Current phase status: implementing foundation; **gate open**. Scalar locals/expressions/branches/arguments are recorded working subsets. Functions, loops and broader semantic contracts remain missing at the inspected baseline. Completing only loops/functions will not by itself close every M1 requirement.

### M2 — first real plugins and versioned state

Depends on M1 invocation semantics, callable metadata and revision ownership.

- M2-A: Real script command trees register/unregister through supported Paper APIs: multiple commands, aliases/subcommands, typed/optional/default arguments, permission-aware suggestions, sender restrictions and collision diagnostics. Test removed nodes after unload and failed-registration cleanup.
- M2-B: Typed events provide validated contexts, priority/cancellation rules, synchronous/asynchronous contracts and no duplicate retired handlers. Owned delay/repeat/cancellation resumes in the correct context and re-resolves disappearing entities; expired event continuations cannot cancel completed events.
- M2-C: Public player/item/location operations support teleport and kit workflows with explicit cloning, stable identities, permissions and thread rules. Scripts use typed APIs rather than feature-specific Java handlers or console strings.
- M2-D: Namespaced persistent gameplay variables use UUID player keys and supported snapshots, SQLite transactions/migrations, documented flush/durability/recovery behavior and bounded off-thread I/O. Expose authoritative unflushed reads, per-key revisions and change hooks for M3. Never persist live Bukkit objects.
- M2-E: Homes/warps, kits and join rewards run as public scripts. Connected-player tests exercise permissions, argument suggestions, item delivery/capacity, persistent cooldowns/locations across restart, delayed cancellation/disappearance, invalid-update preservation and reload/unload cleanup. Record exact engine/Java/Paper versions and separate mock/unit from server evidence.

Current phase status: specified; **gate open**. Revision persistence, `/ais run`, and transport authorization are useful foundations but do not satisfy these gameplay gates.

### M3 — rich gameplay and safe variable administration

Depends on M2 live versioned variables, command/event/scheduler adapters, stable value codecs and ownership.

- M3-A: Public GUI primitives support isolated per-player sessions, callbacks, refresh/pagination and owned cleanup. Connected-client tests cover shift-click, drag, number-key and double-click transfer exploits plus concurrent users/reload.
- M3-B: Implement documented typed subsets of components/item data, recipes, effects, combat/world APIs and scoped configuration/JSON. Each claimed operation has a public example, positive/failure tests, exact platform scope and cleanup/thread behavior. Tagged items persist correctly; effects/recipes do not orphan resources after reload.
- M3-C: Runnable public shop, custom-ability and persistent quest examples compose these APIs. Shop tests include simultaneous purchases, inventory capacity and failure/reconciliation; declare a local test economy or actual provider requirement explicitly, with optional provider adapters still tracked under M4. Quest records/functions/custom events and restart behavior have evidence.
- M3-D: ADD-11 explorer chooses and documents local/self-hosted versus external transport before implementation, authenticates users, separates browse/edit permissions, scopes namespaces, expires sessions and redacts secrets. Snapshot authoritative unflushed state; browse/search/filter/page bounded datasets and typed core editors. Unsupported types remain inspectable/read-only. Define excluded local execution scopes.
- M3-E: Browser edits remain staged. Show exact affected-key diff; reauthorize/validate on apply; cancellable pre-apply hooks preserve all targets on cancellation. Reject stale per-key revisions, source/destination rename collisions and subtree additions. Document batch atomicity/durability; use normal state/change hooks, single-use authorization, safe retries and redacted audit. Test replay/expiry/unauthorized access, invalid types, absent providers, failed persistence, lost responses and restart survival.

Current phase status: specified; **gate open**. A text inspection command or generic data bridge cannot close ADD-11. Authorization, conflict detection and safe apply are required at the first write-capable editor release; they are not deferred to M6.

## Remaining major scope after halfway

M4 still requires an external sample addon using only public SDK contracts (type/event/function/serializer), version/dependency handling, editor codec extensions and independently tested placeholders, permissions/economy, WorldGuard/WorldEdit integrations. M5 adds researched, version-pinned trusted Java, packets, HTTP/Discord, Redis/proxy and NPC/custom-content adapters. M6 consolidates durable lifecycle/crash recovery, profiler, editor/LSP/package/migration tooling, stress tests and all flagship examples. Auction house, Discord bridge, cross-server service, NPC/provider-dependent quests and many advanced Minecraft operations remain explicit backlog items, not implied halfway support.

Production evidence still requires 100 reloads with connected players, active timers and open menus plus measured owned-resource stability, crash/restart boundaries and load conditions. Existing console-only 100-reload records are useful regression evidence but cannot close this broader gate. No Skript source/binary addon compatibility, Folia support, generic “any plugin” claim or percentage of ecosystem parity follows from M1–M3.

## Updating this checkpoint

For each completed slice record concrete capability IDs, source/public interface, runnable example, test command/result, server/client/provider scope, limitations and next task. Link new evidence under its actual release; preserve historical files. The next implementation dependency is bounded invocation control flow/functions and their semantic/authoring contract, followed by real owned command registration and versioned gameplay state. Release/version/changelog/commit/publish steps belong to the enclosing repository change batch under AGENTS.md; this checkpoint alone is not a release.
