# Dependency-ordered backlog

This is a dated ecosystem inventory. Its implementation statuses describe the recorded checkpoint. For current runtime, connectivity and verification status, use the maintained README, language guide, MCP reference and docs/testing.md.

Milestone identifiers follow the implementation brief. Published release numbers follow the repository release policy. A completed change batch receives its own version, changelog, tested artifacts and release.

| Order | Work package | Matrix dependencies | Acceptance checkpoint |
| --- | --- | --- | --- |
| 0 | Maintain the baseline audit and exact provider records | BASE-01..12 | Every current claim links to source/evidence, and future provider support has an explicit research task |
| 1 | First textual frontend slice | CORE-01.01..06 | Locals, typed expressions and conditions lower into the existing message model. Syntax/type failures report source locations. Existing structured definitions and invalid-edit preservation pass |
| 2 | Runtime value/expression IR and invocation scopes | CORE-01.07, .09..11, .17; SDK-01 | Invocation values, missing/conversion/equality rules and state lifetimes are specified and behavior-tested. Preparation retains complete type checking |
| 3 | Bounded control flow, functions and package semantics | CORE-01.12..16; SDK-02..03 | Functions, loops and recursion consume explicit budgets, return useful errors and support reusable pure script packages |
| 4 | Owned resource preparation and real command adapter | OPS-01; CORE-02.01..03 | Commands/aliases/subcommands/arguments/suggestions register through supported Paper APIs, reject collisions and disappear after unload |
| 5 | Versioned persistent variables and SQLite | CORE-06.01..02, .04..05 | Live namespaced values expose per-key revisions/change hooks. UUID keys and supported snapshots survive the declared flush/restart boundary |
| 6 | Typed player/location/item values and scheduler | CORE-04.01, .06, .09..10; CORE-05.01..03 | Delays cancel on retirement, references resolve at resumption and disappeared targets return defined errors |
| 7 | First real plugins and event foundation | CORE-03.01..03, .07..08; work packages 2..6 | Persistent homes/warps, kits and join rewards run through public scripting interfaces with reload/restart fixtures |
| 8 | Safe reusable gameplay/UI primitives | CORE-03.04..06; CORE-04.11..16; ADD-03.01..02 | Menus isolate players and reject shift-click/drag/number-key/double-click exploits. Displays/effects/tasks clean up |
| 9 | Variable explorer transport and staged typed editor | ADD-11.01..12, .14; work package 5 | Authorized scope is snapshotted, browser edits remain staged, stale keys/subtrees conflict, cancelled/failed batches preserve all targeted values and retries cannot double-apply |
| 10 | External SDK and initial optional integrations | SDK-04..06; ADD-07, ADD-09; EXT-01..03 | External sample addon registers a type/event/function/serializer. Missing/incompatible providers produce dependency errors and clean teardown |
| 11 | Addon editor codecs and expanded Minecraft coverage | ADD-11.13; ADD-01, ADD-04, ADD-06, ADD-10; CORE-04 remaining rows | Addon metadata extends the editor and generated reference. Each selected API has a public example and tested version scope |
| 12 | Advanced optional/trusted adapters | ADD-02, ADD-05, ADD-08; EXT-04..18 | Select and audit exact APIs/licenses, implement one vertical provider slice at a time, and run real integration fixtures |
| 13 | Production/tooling consolidation | OPS-02..04; TOOLS-01..03; EXT-19 | All flagship examples pass, crash boundaries match durability claims, connected-player reload/resource metrics and profiler/editor tooling are recorded |
| 14 | Authenticated server-connected authoring/deployment | AI-01..02 and completed authority/runtime gates | Scoped reads first, then independently authorized deployment with revocation/replay/stale/approval/crash/bypass tests |

Dependency edges may move a small primitive earlier when a working example requires it. Each change must retain the existing permission, generation, validation and thread-context guarantees.

## Flagship acceptance map

| Flagship | Public capabilities required | Required failure and lifecycle evidence | Current checkpoint |
| --- | --- | --- | --- |
| Homes/warps/teleport requests | Functions, command arguments, persistent locations, delay/cancel and player teleport | Named homes survive restart, denied users cannot mutate them, delay cancels on retirement/disappearance, removed command vanishes | Specified. Runtime scopes/functions precede the first `/home` implementation |
| Kits | Typed items, persistent cooldowns, permission-aware completion | Clone semantics, inventory capacity, cooldown restart policy and missing permission | Specified |
| Economy shop | GUI, provider responses, persistent operation IDs and delivery reconciliation | Concurrent purchases, insufficient funds, provider outage and crash between payment/delivery | Specified |
| Claims | Spatial queries, protected-event cancellation and effective region flags | Overlap/priority/membership tests, already-cancelled event policy and provider absence | Specified |
| Quests/NPCs | Records, custom events, persistent progress and optional NPC services | Duplicate events/rewards, migrations and restart without duplicate NPC | Specified |
| Arena minigame | Teams, timers, owned resources, disconnect/rejoin and bounded reset | Cancellation, concurrent arenas, reload cleanup and interrupted reset | Specified |
| Custom boss | Combat events, effects, tasks and optional mob/model providers | Owned resource cleanup and version/provider failure | Specified |
| Auction house | Typed SQL transactions, bid versions, expiry and durable operations | Concurrent bids, lost acknowledgement, crash/restart and expiry idempotency | Specified |
| Discord bridge | Async interactions, rate limits, secrets and reconnect lifecycle | Timeout/failed reply/retry and credential redaction | Specified |
| Cross-server feature | Authenticated proxy companion, routing and message identity | Two-server transfer, duplicate/reconnect/revocation/partition fixtures | Specified |
| Variable administration | Versioned live variable service, scoped transport, typed codecs and staged apply | Stale gameplay value, rename/subtree conflict, replay/expiry, cancelled batch, unflushed snapshot, persistence failure and lost response | Specified. M2 variable service precedes M3 editor |

## Next concrete task after the initial frontend

Introduce an invocation-time typed expression/statement representation with owned call frames, preserving the existing parser/type-check boundary and static message adapter. Specify value equality, conversions, missing values, mutability and budgets before functions and loops. Real command registration follows that runtime foundation, then persistent location/player operations enable a reusable `/home` script. Track every completed subset in the matrix with its own example and evidence.
