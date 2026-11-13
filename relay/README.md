# Personal AetherScript relay

This optional Worker joins two outbound HTTPS clients: a Paper connector and an MCP client. It is an application mailbox, with no TCP forwarding, arbitrary URL fetch, shell, console, filesystem, or generic proxy operation. The Paper connector remains authoritative for server-side grants and script validation. The relay can see payloads; this version uses transport TLS, not end-to-end encryption.

## Owner setup

Install Node.js, run `npm ci` here, authenticate Wrangler with `npx wrangler login`, then generate a random enrollment secret for this personal installation. Run `npx wrangler secret put RELAY_REGISTRATION_TOKEN` and paste it at the hidden prompt. Put the same secret in the private Paper connector configuration. Never ship this secret in a JAR, publish it in source, or paste it into chat or command arguments. Restrict configuration file access to the server owner. Rotate the Wrangler secret and connector configuration to stop future enrollments. Revoke active sessions separately.

Deploy with `npx wrangler deploy` only after reviewing the account and Worker name. Use its HTTPS origin as the connector relay endpoint. `wrangler.toml` creates a SQLite-backed Durable Object namespace, supported on Workers Free. No paid product, custom hostname, inbound port, or router change is required. This is personal owner-controlled enrollment; a public multi-tenant service needs a browser enrollment flow and separate abuse protection first.

The server owner selects script grants and creates a session. Copy its single `aether://pair?relay=<encoded HTTPS origin>&code=<sessionId.random>` string into the gateway pairing command. Treat that string as a secret. Pairing is single-use and expires after five minutes. Each session has a separate random server and client token, stored as SHA-256 hashes at the relay; tokens never appear in URLs. There is no list-sessions or public server directory route. Either endpoint can revoke a session immediately. Sessions expire within 24 hours; an alarm deletes stored state. The bounded mailbox clears queued arguments before storing a result, keeping the single storage value below the Durable Object 128 KiB value limit.

## Wire contract

All routes use POST JSON. Session routes require `Authorization: Bearer <endpoint token>`. Responses are JSON with `Cache-Control: no-store`. Timestamps use epoch milliseconds. Production requires HTTPS; `ALLOW_LOCAL_HTTP=true` permits only loopback development URLs.

- `/v1/sessions`, authorized by owner enrollment secret: `{serverId, scopes, ttlSeconds?}` -> 201 `{sessionId, serverToken, pairingCode, expiresAt, pairExpiresAt}`. `serverId` is 1–64 ASCII letters/digits/underscore/dash. TTL defaults to 86400 seconds, range 60–86400.
- `/v1/pair/claim`: `{code: pairingCode}` -> `{sessionId, serverId, token, scopes, expiresAt}`.
- `/v1/sessions/{sessionId}/requests`, client only: `{operation, arguments:{...}}` -> 202 `{id}`.
- Same base `/poll`, server only: `{}` -> `{requests:[{id,operation,arguments}], pollAfterMs:5000}`. At most one request is delivered once. Server reconnect does not replay delivered work.
- `/respond`, server only: `{id,result}` or `{id,error:{code,message}}` -> `{accepted:true}`.
- `/results`, client only: `{id}` -> `{pending:true,pollAfterMs:5000}` or the response envelope. A completed response is consumed once. Pending work expires after two minutes on any authenticated request (including server polling), even if the client crashes. Results polling at expiry returns a timeout error; previously expired/consumed IDs return unknown_request. A timeout is not proof the server did not apply a change: inspect the script before retrying.
- `/revoke`, either endpoint: `{}` -> `{revoked:true}`.

Only explicitly granted `status`, `scripts.list`, `scripts.read`, `scripts.validate`, `scripts.apply`, and `scripts.test` are accepted. Apply uses the connector's optimistic expected-hash checks. The relay allows a 64 KiB UTF-8 JSON envelope (including JSON quoting and metadata, so a full 64 KiB script will not fit; use smaller scripts), one pending request, and 1000 operations per session. It does not interpret script paths or source; the connector enforces `.aether`, path containment, source limits, and approvals. Five failed pairing attempts lock further claims for that session. Unknown operations and cross-endpoint use fail closed.

Clients should poll only while active, every five seconds at most, and back off to 15–60 seconds while idle or after errors. A server polling every five seconds uses about 17,280 requests/day; a continuously waiting client adds a similar amount, excluding operations. Free plan quotas are account-wide and finite; this implementation does not promise unlimited traffic or protect an Internet-facing service from all request quota exhaustion. Exhaustion stops remote operation; local server operation remains independent.

The Worker has no application logging. Do not enable request body/header capture in account observability. Responses and source remain transient until consumed, revoked, or expired. SQLite storage is per session; no external third-party analytics is used.

## Verification

`npm test` runs against real local workerd/SQLite Durable Objects via Miniflare: single-use pairing, operation grant checks, endpoint-role checks, private-session token isolation, request/reply transport, one-request limit, payload limit, registration authentication, expiry range, pairing-attempt lockout, and immediate revocation. The abandoned-request timeout regression additionally uses the same handler with fake storage/time data. These tests run locally. Versioned end-to-end release evidence is recorded under `docs/evidence/`.

Cloudflare references: [SQLite storage](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/), [migrations and Free plan availability](https://developers.cloudflare.com/durable-objects/reference/durable-objects-migrations/), [limits](https://developers.cloudflare.com/durable-objects/platform/limits/).

## 0.0.7 shared browser onboarding candidate

V2 adds `/connect`, OAuth authorization-code enrollment with mandatory S256 PKCE, and the hosted `/mcp` endpoint. The Paper console creates a single-use, ten-minute link. Its secret stays in the URL fragment, is removed from browser history immediately, and becomes a short-lived HttpOnly/SameSite browser session. Approval shows the callback origin and a finite set of exact script IDs; arbitrary dynamic-registration names are never treated as verified app identities. The browser does not display access tokens. Codex uses the Windows setup helper; compatible remote clients use OAuth. V1 remains available for existing personal deployments.

Paper generates its permanent random server credential locally. The relay stores its verifier and immutable client grants. A grant is usable only after Paper persists and acknowledges it. A successful client-initiated status request answered by that Paper instance sets the browser's Connected marker; approval or a browser-only probe cannot set it. Server names are display labels only, inserted with textContent; UUIDs remain the authorization identity. Access tokens/grants expire after thirty days and may be revoked earlier; this candidate does not implement refresh tokens, so reconnect after expiry.

Bounds: ten combined enrollment/registration attempts per hashed IP per UTC day; 1000 globally per day; ten links per server per day; at most 500 permanent server identities and 500 retained OAuth registrations; sixteen active connections per server; 500 operations per connection per day; one pending operation per connection. The permanent server cap fails closed when full and needs operator lifecycle management before increasing it. OAuth registrations expire after ninety days. Codes expire after two minutes and can be redeemed once. Daily Durable Object alarms delete expired codes/registrations, old quota counters (two-day retention), expired grant credentials, expired browser sessions, and pending data. Alarm scheduling is not postponed by continuing traffic. Revoked grant metadata is retained for at most one day, then removed.

The 64 KiB JSON envelope limit remains. V2 additionally caps an encoded request at 44,000 bytes and aggregate public grant metadata at 16,000 bytes, keeping a poll containing one request under the connector's 64 KiB response cap. Source quoting and UTF-8 encoding count against the transport bound. Large grants/requests fail with explicit quota/transport errors. Separate pending-data storage keeps individual Durable Object values bounded; the identity record has a 100,000-byte cap.

Validation: five tests pass locally, covering the preserved v1 suite, real workerd/SQLite browser OAuth enrollment with exact-ID rejection, PKCE, single-use approvals, real client status acknowledgement and revocation, plus a fake-storage alarm retention regression. Native Codex login, public Cloudflare deployment and actual Paper-over-Internet results are separate verification steps, not claimed by these tests.
