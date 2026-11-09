# v0.1 architecture and decisions

Three Gradle modules keep the first vertical slice small:

- `core`: immutable feature/command/message records, safe YAML-node and strict JSON parsing, deterministic IR hash, source directory validation, immutable registry, basic audit sink.
- `plugin-paper`: one static `/ais` dispatcher, player permissions, Adventure plain-text delivery, main-thread activation and effects, one bounded background I/O worker. Plugin disable cancels scheduled callbacks, retires all features, and stops its worker.
- `gateway-mcp`: isolated stdio process using the same parser; no filesystem/server adapter and no mutations.

Parsing and audit I/O run off the server thread. Prepared candidates are validated as a whole; registry activation and all sender operations run on Paper's main thread. Each invocation captures an immutable snapshot; retirement or permission revocation stops subsequent effects. One reload may be pending. Administrative permission is checked again immediately before activation.

The development namespace is `dev.aetherscript`; no claim is made that the project owns a domain. Change it before public publication if an owner-controlled namespace is required.

Differences from the proposed handoff: only one command per feature; no alias; no automatic startup activation; no actual bridge; early MCP is offline. The handoff's durable revision/recovery design remains v0.2, and neither local audit nor immutable snapshots are presented as crash-safe deployment. Configuration limits are fixed constants in v0.1 rather than an unimplemented configurable security surface. Bundled `config.yml` documents the permanently absent bridge; runtime does not use it to enable anything.

Future events should use engine-owned listeners, remove them via the public lifecycle, reject unsupported asynchronous event semantics, and carry generation identity into queued tasks. GUIs, inventory APIs, and dynamic command lifecycle are not implemented or verified here.
