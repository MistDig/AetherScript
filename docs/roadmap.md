# Roadmap and exit gates

## Release 0.0.4 — native authoring

The first language slice prepares checked expressions, local bindings and conditional sender messages. The CLI, Paper and read-only MCP share a compiler. The next engine milestone adds invocation-time expressions before functions, events and persistent player state. Follow the [dependency backlog](ecosystem/backlog.md) and [capability matrix](ecosystem/capabilities.csv) for the scope and acceptance criteria of each feature.

## Release 0.0.2 — recovery and diagnostics

Implemented: saved successful revisions, startup loading/recovery, field/line/column diagnostics, running-versus-editable status, bounded diffs, history and rollback held across restarts. Default retention is five, configurable 1–50. Tests include abruptly halted JVMs and real Paper recovery/corruption checks. This is a subset of durable deployment work, not completion of the security milestone.

Next gameplay design: reusable block-break events, location state and displays for a ten-heart block. Support individual blocks, materials, regions and all-block scopes with permissions/quotas. Define protection-plugin compatibility, chunk unload, replacements/explosions, concurrent damage and cleanup before publishing syntax.

## v0.1 — local slice (in progress)

Implemented: safe YAML/JSON, typed message actions, source/IR hashes, immutable generations, local administration, permission-checked invocation, explicit live reload, rejected-update preservation, bounded I/O, basic audit. The owner reported connected-player reload/rejection/permission checks passing on 0.0.1; repeat on 0.0.2 and measure resource behavior before declaring full milestone acceptance. Read-only offline MCP is an experimental parallel track, not completed v0.3.

## v0.2 — trustworthy deployment

Durable content-addressed revisions and journal; prepare/commit/status/rollback; checksummed startup recovery; crash injection; idempotency; concurrent plan rejection; rollback under current policy; authoritative plugin capability policy; independently authenticated loopback bridge; principal scoping; credential rotation/revocation; quota enforcement; redacted scoped logs; retention/rotation. Exit: direct unauthorized bridge requests and stale/revoked approvals fail, including at every crash boundary.

## v0.3 — server-connected MCP

Integrate the gateway through a narrow authenticated bridge; scoped reads first. Add stage/plan/deploy only after v0.2 server-side checks and bypass tests. Bind approvals to principal, server, content, dependencies, policy, and generation. Test client versions, reconnect, timeouts, denied/allowed deployment, and unknown transaction recovery in Codex, Claude/Claude Code, Cursor, and Cline. Use a maintained MCP SDK after compatibility/security evaluation.

## v0.4 — events, UI, and bounded timers

Allowlisted events, bounded scheduled work and per-player cooldowns, managed GUIs, scoreboards, and boss bars. Scope UI ownership per player/feature, respect other plugins' displays, clean up on disconnect/reload, invalidate stale callbacks, and enforce per-feature task/UI limits. Cooldowns need explicit clock semantics, restart policy, and generation-aware cancellation.

## v0.5 — state and richer gameplay

Typed persistent player data with schema versions, migration review, backups, atomic updates, retention/privacy controls, and restart tests. Custom items with server-owned identity/PDC, allowlisted components and recipes, duplication/transfer tests, ownership checks, and clear interoperability rules. Feature dependencies, monitored execution budgets, and cautious feature-attributed automatic rollback.

## Later — economy and quests

Economy adapters with explicit currency/precision, permissions, idempotent transfers, atomic balance updates, double-spend/concurrency tests, audit trails, and reviewed integrations (such as Vault where supported). Do not model financial transfers as reversible registry updates.

Quest definitions with bounded state machines, persistent progress, dependency validation, event deduplication, reward idempotency, abuse-resistant objectives, party scope, and safe migration of active quests. Compose scoreboards, boss bars, custom items, cooldowns, and player data through the same typed IR/capability system.

## Secure remote connectivity (gated, not enabled)

Owner-provisioned server identities; TLS/mTLS or a reviewed private tunnel; audience/expiry-bound credentials; verified delegation; replay defense; origin/host protections where applicable; least-privilege scopes; revocation; connection quotas; audit visibility and explicit production approval. Test cross-server impersonation, revoked credentials, network partitions, lost acknowledgements, retry duplication, and compromised gateways. No generic remote shell, server-directory write, console-execution, or plugin-install endpoint.

## Language and extensibility

A human DSL compiles to the same IR, with source maps/editor support and no promise of Skript compatibility. Review extension SPI permissions and blocking behavior before allowing third-party operations. Purpur requires independent smoke/lifecycle tests; Folia and client mods remain outside the current compatibility claim.
