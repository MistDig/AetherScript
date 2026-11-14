# Architecture

Three modules share one bounded, typed engine:

- `core`: bounded native text frontend and strict YAML/JSON parser with source locations; immutable definitions/registry; source inspection; revision storage/recovery; strict owner settings; local audit.
- `plugin-paper`: static `/ais` dispatcher, permissions, Adventure plain-text messages, main-thread activation/effects, one bounded I/O worker.
- `gateway-mcp`: isolated stdio process using the same parser. It reads an owner-selected private profile for loopback status or scoped relay operations. Offline validation and exact-message tests share the parser and Registry.

## Activation and durable state

The worker reads and validates source, checks the whole candidate registry, stages content-addressed source and records audit intent. The main thread rechecks administrator permission and swaps the immutable registry at the expected generation. The worker atomically commits checksummed metadata and records success before replying. Unchanged source adds neither history nor a generation.

A save or completion-audit failure attempts to restore prior metadata and features. Restoring the registry uses a new generation; effects in the brief activation-to-commit window cannot be undone. Failed compensation quarantines the engine. This local sequence is not an authenticated distributed transaction protocol.

One state-changing/inspection request is admitted at a time. The main thread never blocks on disk: the worker waits for scheduled main-thread boundaries. Invocations capture a snapshot and check retirement and permissions before subsequent effects. Disable cancels callbacks, stops the worker and clears the registry.

## Storage

`storage/revisions/<id>/<sha256>.source` holds immutable committed content, except repair with validated identical bytes when corrupted. `storage/manifest.json` stores history, active pointers and rollback holds in a checksummed envelope. Files are size-bounded, paths checked, source IDs revalidated. The manifest is the authority; loose/pending files are not automatically imported.

Writes use a forced temporary file and required atomic replacement. Directory flushing is best-effort where supported; no hardware/power-loss guarantee. Default retention is five (configurable 1–50), protecting active versions. At most 128 histories are tracked, including retired features. Pruning runs after success; owner backups remain necessary.

Startup normally loads valid editable files. Invalid/missing sources fall back to active or earlier readable committed revisions. Invalid new sources do not stop unrelated valid features. Corrupt metadata blocks startup. Explicit rollback is held across restarts until reload; editable files are never overwritten by rollback. Command collisions are checked before registry replacement.

## Current boundaries

One command per feature; only `message.send` to sender; no aliases, events, mutable runtime variables, world mutation or general console execution. Preparation-time local values/conditionals and argument-based invocation-time programs are supported. Opt-in loopback status and outbound remote authoring are separate authenticated surfaces. Config only selects supported retention, not security bypasses. Future events/tasks must carry generation identity and implement cleanup, threading and quota rules before shipping.

See `docs/wiki/Revisions-and-Recovery.md` for operator behavior and `docs/roadmap.md` for security gates.

## Native script preparation

The text frontend checks local bindings and both branches of a conditional during preparation. Legacy static scripts evaluate the selected branch during preparation. Programs using arguments, mutable locals, loops or functions evaluate per invocation and buffer the same immutable sender-message actions used by structured features. The Paper invocation path checks the command permission and sender restriction. Offline validation uses the same compiler through the CLI and MCP. The packaged JSON authoring contract supplies the template for the website and both tools.

## Scoped relay authoring

The optional `relay/` Worker is a bounded application mailbox between two outbound HTTPS clients. The owner enrolls Paper with a private registration secret; console `ais remote <ids>` installs an in-memory allowlist and prints a private five-minute pairing URI. The gateway claims it and persists an owner-restricted profile. Distinct server/client roles, operation scopes and token hashes protect the mailbox; the plugin independently checks exact arguments, IDs and its local grant.

Only `.aether` files can be created or edited. Apply validates the whole candidate registry, records a forced recovery intent, and activates through the normal durable pipeline with a main-thread grant recheck before publishing the editable file by source-hash compare-and-swap. Startup resolves interrupted publication using the durable manifest sequence and active hash: uncommitted candidates are discarded; committed candidates are published only while the editable source still matches the original expected hash. A later owner edit is preserved. Unchanged durable plans use a guarded source update without a recovery intent. A delivery failure after commit never restores an older source. Readback includes editable/active hashes, generation and revision; inspect unknown outcomes before retrying.

Source checks and atomic replacement serialize plugin-managed updates. An external editor does not participate in that lock: avoid editing the same feature while a remote apply is pending, since portable Java cannot atomically compare file contents and replace them against an unrelated writer. File and intent contents are forced; directory flushing is best-effort, so abrupt-JVM recovery tests do not imply a hardware power-loss guarantee.

Live tests schedule actual Registry invocation on the platform thread with a synthetic non-player capability and capture messages. Offline tests use a fresh local Registry. Neither path impersonates a player. TLS is used without end-to-end encryption, so the relay sees source and results. There are no general commands, arbitrary filesystem/network operations or player actions. Remote-off, replacement, restart and expiry end authority. See `relay/README.md` and `docs/wiki/Security-and-MCP.md` for setup and limits.

## Shared browser enrollment

A new plugin installation persists a random server UUID and credential. Console `ais connect` creates a one-use browser delegation. OAuth approval selects a finite feature list, which Paper saves as an immutable local grant before acknowledging the connection. Requests carry a connection ID and are checked against that grant on Paper. Normal restarts restore approved grants. Disconnect blocks immediately and persists revocation before confirming.

Arguments, mutable locals, loops or function declarations select a bounded runtime program. Each invocation owns its local bindings; function calls use isolated frames and a shared step/output budget. Messages are buffered before permission and retirement checks emit them. Legacy source without these constructs preserves preparation-time lowering.

Owned Paper commands are replaced at the same activation boundary as the registry. Foreign labels reject activation, and failed registration restores prior bindings. The new persistent-variable host service uses revision-checked atomic JSON snapshots on a worker thread; it has no script bindings and does not satisfy the SQLite milestone.
