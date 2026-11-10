[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.2)

---

## Grow the language one tested surface at a time

Published versions start at 0.0.1. The original v0.1–v0.5 roadmap labels describe engineering milestones, not automatic patch numbers. Planned features are not valid syntax today.

### Now · 0.0.2

Saved revisions, startup recovery, useful diagnostics, running-versus-editable status, diff, history and rollback. Offline read-only MCP continues alongside it. The ten-heart block system was intentionally deferred from this release.

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

### Secure deployment and connectivity

First implement server-side capability policy, scoped principals, authenticated loopback access, revocation, quotas, bound approvals and crash-safe transactions. Local revisions are one piece, not completion of the remote security milestone.

Connected MCP begins with scoped reads. Plan/stage/deploy wait for authorization and bypass tests. Verify client reconnects, timeouts, unknown results and named clients.

Remote servers need owner-provisioned identities, TLS/mTLS or a reviewed private tunnel, expiring scoped credentials, rotation/revocation and replay defenses. Test impersonation, compromised gateways, partitions, retries and lost acknowledgements. A tunnel does not replace application authorization.

Performance measurements and connected-player checks remain release gates. Purpur needs independent tests; Folia is outside the current claim.

[Engineering roadmap](https://github.com/MistDig/AetherScript/blob/v0.0.2/docs/roadmap.md)

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.2
