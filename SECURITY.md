# Security status

## Shared browser pairing in 0.0.7

Console-only `ais connect` delegates a single ten-minute browser approval to choose explicit feature IDs. The plugin persists its random server identity and immutable grants in `connections.json`. Treat that file and console links as credentials. Local authorization and source validation are enforced on every operation, including after a restart. `ais disconnect` blocks access immediately and saves a revocation tombstone before confirming persistence.

The hosted MCP uses OAuth authorization code flow with mandatory S256 PKCE, registered redirect validation and tokens scoped to a single connection/server. Browser sessions use HttpOnly cookies. The approval page displays the redirect origin and exact script IDs. A client-supplied name does not verify app identity. The pairing page confirms Connected only when the client requests status and Paper answers.

The public service runs on Workers Free with bounded enrollments, registrations, grants, operations, payloads and retention. Relay operators can see scripts and output. Connections expire after 30 days and require new approval. Service outages do not stop local scripts. The 0.0.6 enrollment secret is used only for the personal relay workflow below.


AetherScript 0.0.6 adds opt-in scoped remote `.aether` authoring and retains read-only loopback status pairing. The owner starts the bridge with `ais pair` in the server console. It binds to literal IPv4 loopback on an ephemeral port and grants the fixed `status` scope. The live response contains plugin version, readiness, busy state, registry generation, active feature count, recovery state and AI-deployment state. Offline authoring tools validate caller-supplied source and publish the language/template metadata.

## Pairing and credentials

A console-issued pairing code has five-minute expiry and a single successful exchange. Successful pairing returns a random bearer token with a one-hour session. Re-pairing invalidates the previous grant. `ais unpair` closes the listener and revokes the grant, and plugin disable/restart ends in-memory access. Pairing attempts, request size, request rate, worker threads and queued work are bounded. The bridge validates a literal loopback Host header and rejects browser Origin/Sec-Fetch-Site requests.

The CLI reads the pairing code from a hidden Java-console prompt or standard input. Tokens and codes have no command-line flags. The profile stores the endpoint, scope, token and expiry with owner-restricted file permissions, a bounded strict format and atomic replacement. Keep it outside source control and shared folders. The code is displayed in the owner console, so console and log access must remain restricted. Profile possession grants status access until expiry or server revocation. Local `forget` removes the file, while server `unpair` revokes clients that already hold the token.

`plugins/AetherScript/connection-details.json` is an informational endpoint/scope/transport record. It contains no code or token and can remain stale after shutdown. Startup/unpair removes it, and a fresh pair command prints the current endpoint.

## Transport and trust

The gateway accepts `http://127.0.0.1:PORT` with a literal host, numeric port and an empty/root path. User information, query strings and fragments are rejected. It disables redirects and proxy use, bounds response size and timeouts, and validates returned status fields. For another server host, use an authenticated encrypted loopback tunnel, such as SSH, with both forwarded bindings kept on loopback. The local HTTP connection relies on the operating system's host boundary and the bearer credential. Installed Java plugins, local host administrators, SSH administrators and the MCP process owner remain trusted.

MCP tool annotations describe behavior. The plugin's fixed grant and endpoint checks authorize the actual status request. The status surface supplies no script source, server logs, player details or credential values. This status credential cannot authorize mutation. Scoped remote writes use the separate console grant described below. General console execution remains unavailable.

## Script and storage authority

Local `/ais` administration requires `aetherscript.admin`, with activation checking again on the main-thread boundary. Pair/unpair use the trusted server console. Runtime messages require the feature's explicit permission and sender rule. YAML/JSON and textual frontend validation reject unsupported operations, unsafe logical IDs and oversized input. The text frontend performs bounded typed preparation. Source files must be regular files in the owner-managed scripts directory. Dependencies are shaded/relocated in the plugin.

Saved source revisions are checksummed and revalidated under the current parser policy. Registry rollback selects working definitions while already-dispatched effects remain observable. Local revision recovery has process-crash tests. Filesystem directory-flush support varies, and owner backups remain part of recovery. Checksums detect corruption under the trusted-host model.

Broader gameplay authority, general deployment plans and per-edit approvals remain future work requiring separate authorization and bypass tests. The status credential cannot be upgraded by a gateway request.

## Scoped remote authority and trust

Only the actual server console can issue `ais remote <id[,id]>` and `ais remote-off`. The in-memory feature allowlist is checked on every request and again at activation. New grants replace earlier grants; disable/restart ends local authority. The closed vocabulary permits status and authorized script list/read/validate/apply/test. No general commands, shell, arbitrary file paths or player impersonation are exposed.

The owner deploys a personal relay and puts its enrollment secret in the hosting account and private `plugins/AetherScript/relay.json`. No public deployment or bundled secret is supplied. Protect that configuration with owner-only OS permissions: the connector validates shape and regular-file status but does not enforce its ACL. Enrollment permits session creation; the plugin remains authoritative for feature access.

The console prints a five-minute single-use pairing URI. Treat it as a secret; paste only into the local CLI prompt/stdin. Console/log readers can see it. The gateway persists an owner-restricted profile. Sessions last at most 24 hours, with distinct server/client bearer tokens stored as hashes at the relay. `remote-off` revokes local authority immediately and attempts relay revocation. Local `forget` removes only the file. Rotate enrollment secrets to prevent new enrollment and revoke active grants separately.

Both clients use outbound HTTPS origins with redirects disabled. TLS protects transit; **the cloud relay can see script source, arguments and results**. There is no end-to-end encryption. Hosting administrators/provider infrastructure, server administrators, installed plugins and the MCP process owner are trusted. Do not enable body/header capture in relay observability.

Remote source writes are confined to `.aether` files under the scripts directory using validated logical IDs, containment, format and regular-file checks. YAML/JSON features cannot be replaced remotely. Apply validates source and registry, checks an expected source hash (`ABSENT` for creation), writes atomically and activates through the durable engine with a grant recheck. Failed activation attempts source restoration. Lost replies/timeouts can have unknown outcomes: inspect editable and active hashes before retrying.

Synthetic tests run the actual Registry with a non-player capability and capture exact messages. They cannot impersonate players or satisfy player-only sender rules. Normal runtime permission/sender checks remain in force. Tool annotations describe behavior; relay role/scope checks and independent plugin target checks enforce authority. See release evidence for actual bypass/revocation verification.

The relay caps JSON envelopes at 64 KiB including quoting/metadata, with one pending operation and 1000 operations/session. Scripts must fit within that envelope. Five failed pairing claims lock a session. Abandoned work expires after two minutes; gateway waits are separately bounded. Server polling normally occurs every five seconds. Hosting quotas are account-wide and finite; availability and public request-quota exhaustion protection are not guaranteed. Local `/ais` operation is independent of relay availability. See [the operator guide](docs/wiki/Security-and-MCP.md) and [relay setup/limits](relay/README.md).

## Reporting

Report security issues privately to the repository owner through an agreed private channel. A dedicated reporting contact is still pending. Keep real codes, profiles, tokens and private logs out of public issues. Local audit files need owner-controlled OS access and disk monitoring. Audit rotation and remote audit integrity remain tracked operational work. [Repository](https://github.com/MistDig/AetherScript)

## Full access and OAuth recovery in 0.0.8

Full native-script access is an explicit immutable boolean in the browser approval, relay and persisted Paper grant. Missing fields in older connections remain false. Console-restricted approvals cannot acquire full access. Full access permits editing existing .aether files and creating new valid feature IDs; legacy source-format collisions remain protected. Changing access requires fresh owner approval.

New connect commands supersede uncompleted enrollment, approval and OAuth sessions. Active acknowledged token-bearing connections remain valid. Server rejections terminate only pending offers. Expired or terminal tokens receive an OAuth invalid_token challenge so clients can sign in again. The relay waits briefly for valid pending Paper acknowledgement; a longer outage returns a retryable service error.

The OAuth service supports none, client_secret_basic and client_secret_post authentication. Registration secrets are per-client, shown once, and stored as verifiers. The manual setup form is available from a private pairing browser session. Public metadata and token endpoints support configured web client origins; credentialed pairing cookies remain restricted to the service origin.
