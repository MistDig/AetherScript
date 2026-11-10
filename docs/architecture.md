# Architecture — 0.0.2

Three modules share one bounded, typed engine:

- `core`: strict YAML/JSON parser with source locations; immutable definitions/registry; source inspection; revision storage/recovery; strict owner settings; local audit.
- `plugin-paper`: static `/ais` dispatcher, permissions, Adventure plain-text messages, main-thread activation/effects, one bounded I/O worker.
- `gateway-mcp`: isolated stdio process using the same parser. No server bridge, filesystem reads or mutation tools.

## Activation and durable state

The worker reads and validates source, checks the whole candidate registry, stages content-addressed source and records audit intent. The main thread rechecks administrator permission and swaps the immutable registry at the expected generation. The worker atomically commits checksummed metadata and records success before replying. Unchanged source adds neither history nor a generation.

A save or completion-audit failure attempts to restore prior metadata and features. Restoring the registry uses a new generation; effects in the brief activation-to-commit window cannot be undone. Failed compensation quarantines the engine. This local sequence is not an authenticated distributed transaction protocol.

One state-changing/inspection request is admitted at a time. The main thread never blocks on disk: the worker waits for scheduled main-thread boundaries. Invocations capture a snapshot and check retirement and permissions before subsequent effects. Disable cancels callbacks, stops the worker and clears the registry.

## Storage

`storage/revisions/<id>/<sha256>.source` holds immutable committed content, except repair with validated identical bytes when corrupted. `storage/manifest.json` stores history, active pointers and rollback holds in a checksummed envelope. Files are size-bounded, paths checked, source IDs revalidated. The manifest is the authority; loose/pending files are not automatically imported.

Writes use a forced temporary file and required atomic replacement. Directory flushing is best-effort where supported; no hardware/power-loss guarantee. Default retention is five (configurable 1–50), protecting active versions. At most 128 histories are tracked, including retired features. Pruning runs after success; owner backups remain necessary.

Startup normally loads valid editable files. Invalid/missing sources fall back to active or earlier readable committed revisions. Invalid new sources do not stop unrelated valid features. Corrupt metadata blocks startup. Explicit rollback is held across restarts until reload; editable files are never overwritten by rollback. Command collisions are checked before registry replacement.

## Current boundaries

One command per feature; only `message.send` to sender; no aliases, events, variables, world mutation, network listener or AI deployment. Config only selects supported retention, not security bypasses. Future events/tasks must carry generation identity and implement cleanup, threading and quota rules before shipping.

See `docs/wiki/Revisions-and-Recovery.md` for operator behavior and `docs/roadmap.md` for security gates.
