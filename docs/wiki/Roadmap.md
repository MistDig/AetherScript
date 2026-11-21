[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.17)

---

## Grow the language one tested surface at a time

Published versions start at 0.0.1. The original v0.1–v0.5 roadmap labels describe engineering milestones, not automatic patch numbers. Planned features are not valid syntax today.

### Available now

Native message commands support typed arguments, mutable scalar locals, bounded loops and typed functions at invocation time. Legacy commands without arguments or advanced constructs retain preparation-time evaluation. Real owned command names are registered; connected-player command-tree refresh and suggestions remain unverified. Live reload, saved revisions, startup recovery, diagnostics, diff, history and rollback are available. Hosted OAuth MCP can read, validate, apply and test native scripts under selected or full native-script grants. Native join/quit and cancellable block events, item descriptors, storage-inventory operations and durable per-player kit cooldowns are available. The ten-heart block remains planned.

Fresh 0.0.17 browser grants also authorize the supported location, state, delay and self-teleport calls. AI can create and activate those scripts, then run asynchronous tests as the explicitly approved player and inspect actual messages and effect receipts. Legacy grants remain message-only until reapproved; console ceilings still override app permissions.

### Next · reusable gameplay primitives

The block-health idea needs block-break events, location-scoped state, controlled damage/cancellation and displays with cleanup. Build these as reusable primitives, not one hard-coded demo. Design for individual locations, material filters, regions and all-block scopes. Check protection-plugin interoperability, unloaded chunks, replacement/explosion handling, concurrent players, reload/restart behavior and resource limits.

| Area | Design and verification work |
| :--- | :--- |
| Human DSL/editor tools | Same typed IR, source maps, versioned grammar, useful errors and bounded execution. |
| Events/timers | Allowlisted events, correct threading, generation-aware cancellation and cleanup. |
| Cooldowns | Player/feature scope, clock semantics, restart policy and bounded state. |
| Scoreboards | Ownership, other-plugin coexistence, cleanup and update budgets. |
| Boss bars | Player visibility, lifecycle ownership, bounded updates and cleanup. |
| GUIs | Inventory identity, click/drag checks and stale-session invalidation. |
| Custom items | Server-owned identity/PDC, allowlisted components/recipes and duplication/transfer tests. |
| Persistent player data | Typed schemas, atomic updates, migrations, backups and privacy/retention. |
| Economy | Currency precision, idempotent transfers, atomic balances, permissions and double-spend tests. |
| Quests | Bounded state machines, persistent progress, deduplicated events/rewards and safe migrations. |

### BetterModel integration - planned EXT-10

The latest handoff expands model support into typed definition/tracker/bone/playback handles, bone damage/click events, animation markers, per-viewer visibility, player rigs, rendering, hitboxes, paths, spawn rules and an optional Citizens hook. These are planned capabilities. The adapter will target BetterModel directly, without requiring Skript or SkBetterModels.

M2-M3 must first establish synchronous cancellable events, typed numeric/vector values and revision-owned resource cleanup; optional provider SDK/adapters follow in M4-M5. The first provider fixture is a boss with distinct head/body damage and an attack tied to a named animation marker. Pack visibility/rig behavior needs two-client evidence; hitbox resize and alpha look-at behavior require separate version tests.

The [detailed plan](https://github.com/MistDig/AetherScript/blob/main/docs/ecosystem/model-integration-plan.md) and [124-entry upstream registration inventory](https://github.com/MistDig/AetherScript/blob/main/docs/ecosystem/skbettermodels-registration.csv) are specifications, not implemented syntax or verified Paper compatibility.

### Secure deployment and connectivity

First implement server-side capability policy, scoped principals, authenticated loopback access, revocation, quotas, bound approvals and crash-safe transactions. Local revisions are one piece, not completion of the remote security milestone.

Personal-relay authoring was introduced in 0.0.6, shared hosted OAuth in 0.0.7, and explicit full/selected native-script grants in 0.0.8. The separate loopback pairing remains status-only. Broader gameplay authority and per-edit approvals remain future work. Verify client reconnects, timeouts, unknown results and named clients.

Remote servers need owner-provisioned identities, TLS/mTLS or a reviewed private tunnel, expiring scoped credentials, rotation/revocation and replay defenses. Test impersonation, compromised gateways, partitions, retries and lost acknowledgements. A tunnel does not replace application authorization.

Performance measurements and connected-player checks remain release gates. Purpur needs independent tests; Folia is outside the current claim.

[Capability matrix](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/ecosystem/capabilities.csv) · [Engineering roadmap](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/roadmap.md)

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.17
