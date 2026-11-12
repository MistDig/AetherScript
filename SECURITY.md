# Security status

AetherScript 0.0.5 offers opt-in read-only status pairing. The owner starts the bridge with `ais pair` in the server console. It binds to literal IPv4 loopback on an ephemeral port and grants the fixed `status` scope. The live response contains plugin version, readiness, busy state, registry generation, active feature count, recovery state and AI-deployment state. Offline authoring tools validate caller-supplied source and publish the language/template metadata.

## Pairing and credentials

A console-issued pairing code has five-minute expiry and a single successful exchange. Successful pairing returns a random bearer token with a one-hour session. Re-pairing invalidates the previous grant. `ais unpair` closes the listener and revokes the grant, and plugin disable/restart ends in-memory access. Pairing attempts, request size, request rate, worker threads and queued work are bounded. The bridge validates a literal loopback Host header and rejects browser Origin/Sec-Fetch-Site requests.

The CLI reads the pairing code from a hidden Java-console prompt or standard input. Tokens and codes have no command-line flags. The profile stores the endpoint, scope, token and expiry with owner-restricted file permissions, a bounded strict format and atomic replacement. Keep it outside source control and shared folders. The code is displayed in the owner console, so console and log access must remain restricted. Profile possession grants status access until expiry or server revocation. Local `forget` removes the file, while server `unpair` revokes clients that already hold the token.

`plugins/AetherScript/connection-details.json` is an informational endpoint/scope/transport record. It contains no code or token and can remain stale after shutdown. Startup/unpair removes it, and a fresh pair command prints the current endpoint.

## Transport and trust

The gateway accepts `http://127.0.0.1:PORT` with a literal host, numeric port and an empty/root path. User information, query strings and fragments are rejected. It disables redirects and proxy use, bounds response size and timeouts, and validates returned status fields. For another server host, use an authenticated encrypted loopback tunnel, such as SSH, with both forwarded bindings kept on loopback. The local HTTP connection relies on the operating system's host boundary and the bearer credential. Installed Java plugins, local host administrators, SSH administrators and the MCP process owner remain trusted.

MCP tool annotations describe behavior. The plugin's fixed grant and endpoint checks authorize the actual status request. The status surface supplies no script source, server logs, player details or credential values. AI deployment, server-file mutation and console-execution capabilities require future independently tested server-authority implementations.

## Script and storage authority

Local `/ais` administration requires `aetherscript.admin`, with activation checking again on the main-thread boundary. Pair/unpair use the trusted server console. Runtime messages require the feature's explicit permission and sender rule. YAML/JSON and textual frontend validation reject unsupported operations, unsafe logical IDs and oversized input. The text frontend performs bounded typed preparation. Source files must be regular files in the owner-managed scripts directory. Dependencies are shaded/relocated in the plugin.

Saved source revisions are checksummed and revalidated under the current parser policy. Registry rollback selects working definitions while already-dispatched effects remain observable. Local revision recovery has process-crash tests. Filesystem directory-flush support varies, and owner backups remain part of recovery. Checksums detect corruption under the trusted-host model.

Before any write-capable AI deployment, implement independently authenticated principals, default-deny scopes, runtime/player capability intersection, credential rotation/revocation, bound approval records, request quotas, stale/replay checks, durable transaction semantics and direct bridge-bypass tests. The current status credential cannot be upgraded by a gateway request.

## Reporting

Report security issues privately to the repository owner through an agreed private channel. A dedicated reporting contact is still pending. Keep real codes, profiles, tokens and private logs out of public issues. Local audit files need owner-controlled OS access and disk monitoring. Audit rotation and remote audit integrity remain tracked operational work. [Repository](https://github.com/MistDig/AetherScript)
