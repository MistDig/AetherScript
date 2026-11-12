[Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Download 0.0.6](https://github.com/MistDig/AetherScript/releases/tag/v0.0.6)

## Connect an agent to selected scripts

0.0.6 adds opt-in remote `.aether` authoring over an owner-hosted HTTPS relay. The plugin remains authoritative: the actual server console grants a finite set of feature IDs. An agent can list, read, validate, create/edit, activate and test those IDs. This does not grant general Minecraft commands, a shell, arbitrary file access or a player identity.

The gateway also retains offline authoring and the existing loopback status-only pairing. Java 25 is required for both plugin and gateway. Named-client verification is recorded separately in the [client matrix](https://github.com/MistDig/AetherScript/blob/main/docs/mcp-clients.md).

### 1. Set up your personal relay

The owner deploys the Worker using the [relay setup instructions](https://github.com/MistDig/AetherScript/blob/v0.0.6/relay/README.md). This repository does not supply a shared public relay or a bundled enrollment secret. Create a random enrollment secret, enter it into the relay's private `RELAY_REGISTRATION_TOKEN` setting, and put the same secret in `plugins/AetherScript/relay.json`:

```json
{
  "endpoint": "https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev",
  "registrationToken": "REPLACE_WITH_YOUR_PRIVATE_RANDOM_ENROLLMENT_SECRET",
  "serverId": "my-paper-server"
}
```

Use an HTTPS origin without a path, query or credentials. `serverId` accepts 1–64 ASCII letters, digits, underscores or dashes. The enrollment secret must be 32–512 characters. Protect this file with owner-only filesystem access; the connector checks its shape and regular-file status but does not enforce its ACL for you. Keep it out of Git, shared folders and public backups. Relay setup requires owner access to its hosting account. Review that account before deploying.

Both Paper and the local gateway make outbound HTTPS requests. No Minecraft inbound port or router forwarding is needed for the relay.

### 2. Grant one feature from the server console

After Paper reports ready, run this in the actual server console (not player chat or remote command impersonation):

```text
ais remote cloud-note
```

This grants only `cloud-note`, including creating it when absent. To authorize several IDs, separate them with commas. A fresh remote command replaces the earlier grant. Wait for the private `aether://pair?...` link. It is a five-minute, single-use pairing secret. Console/log readers can see it, so restrict those readers. Do not paste the link into Codex chat, a public issue, shell history or command arguments.

### 3. Connect on Windows

Download **AetherScript-Codex-Windows-0.0.6.zip** from the release and extract the complete ZIP into one folder. With Java 25 and Codex installed, double-click **Connect-AetherScript.cmd**. Paste the private pairing link into its prompt.

The helper copies the gateway to `%LOCALAPPDATA%/AetherScript/gateway.jar`, stores an owner-restricted profile under `%LOCALAPPDATA%/AetherScript/profiles/minecraft.json`, and registers the `aetherscript` stdio MCP entry with the installed Codex CLI. Open a new Codex chat after it succeeds. Re-running the helper updates that named entry. If setup fails, check Java 25, Codex availability, relay access and the fresh console grant; extract the entire ZIP rather than opening the helper inside the archive.

Manual pairing on other systems or custom paths:

```text
java -jar AetherScript-MCP-0.0.6.jar pair-cloud --profile /private/path/profile.json
```

Paste the whole link into stdin at the prompt. A real Java-console prompt hides input. Redirected stdin also works. Credentials have no command-line flags. Profile creation can fail after the pairing code is consumed; issue a fresh server grant in that case.

Configure any compatible stdio client to launch:

```json
{
  "command": "java",
  "args": ["-jar", "/absolute/path/AetherScript-MCP-0.0.6.jar", "serve", "--profile", "/private/path/profile.json"]
}
```

The gateway needs no model API key. Client configuration syntax and verification differ by client. Profile possession permits the existing grant until expiry or revocation; protect the profile as a credential.

### 4. Author, activate and inspect observed output

Ask the agent to list authorized IDs, read `cloud-note`, write a small message script, validate it, apply against its current hash, then read it back and test the exact expected message. For example:

```text
feature cloud-note version "1.0.0"
command cloudnote permission "aetherscript.example.cloud" sender any {
  send "Cloud connected!"
}
```

| Tool | Arguments and result |
| --- | --- |
| `aether_server_status` | `{}`; relay mode reports generation and granted target IDs. Loopback mode returns its bounded status snapshot. |
| `aether_capabilities` | `{}`; language/template metadata, tool discovery and profile operation scopes. The server still checks target grants. |
| `aether_validate` | `{source, format}`; offline parser validation for `aether`, `yaml` or `json`. No server authorization check. |
| `aether_test` | `{source, format, expectedMessages}`; offline shared-Registry execution with exact expected versus actual messages. |
| `aether_scripts_list` | `{}`; authorized feature IDs. Relay profile required for this and following tools. |
| `aether_scripts_read` | `{id}`; editable source, `hash`, `activeHash`, `generation` and `revision`; absent source has `hash: "ABSENT"`. |
| `aether_scripts_validate` | `{id, source}`; candidate `.aether` validation against server constraints. |
| `aether_scripts_apply` | `{id, source, expectedHash}`; compare-and-swap source save plus activation, returning readback. Use the read hash, or `ABSENT` for creation. |
| `aether_scripts_test` | `{id, expectedMessages}`; runs the active definition and returns `actualMessages`, `matched`, `invoked`, `sender` and `generation`. |

Check `actualMessages` equals `["Cloud connected!"]`, `matched` is true and `invoked` is true. These are captured effects from the actual Registry, not the agent's prediction. The sender is `synthetic-agent`: tests do not impersonate a player, bypass a player-only sender rule, or deliver a chat message to a real player. Normal `/ais run cloud-note` still checks the real caller's permission and sender rule.

Readback distinguishes editable `hash` and running `activeHash`. A stale expected hash rejects an edit. After a timeout or lost response, read source and active state before retrying: a missing reply does not prove activation failed. Apply cannot rewrite an existing YAML/JSON feature under the same ID; use a new granted `.aether` ID or migrate locally.

### Revoke access

From the server console:

```text
ais remote-off
```

Revocation removes local authority immediately and attempts relay revocation. Server restart/disable ends the in-memory grant. Sessions expire within 24 hours; reconnect by issuing a fresh console grant. Remove a local profile with:

```text
java -jar AetherScript-MCP-0.0.6.jar forget --profile /private/path/profile.json
```

Removing a file does not revoke a credential already held by a running client. Rotating the relay enrollment secret prevents future enrollment; revoke active grants separately.

### Privacy, limits and operational boundaries

TLS protects transit. The owner-hosted relay can read script source, arguments and observed results; this version has no end-to-end encryption. Hosting administrators and provider infrastructure are trusted. Avoid body/header capture in relay observability. Tokens stay outside MCP output; pairing links and profiles must stay private.

The relay accepts a 64 KiB JSON envelope including escaping/metadata, one pending operation and 1000 operations per session. Keep scripts below that envelope budget. The local parser caps source at 64 KiB; this is not a promise that a full-size source fits remotely. Pairing expires in five minutes, with five failed claims locking a session. The mailbox times out abandoned work after two minutes. Gateway calls have a shorter bounded wait, so unknown outcomes require readback. Polling normally runs every five seconds; account-wide hosting quotas are finite. Quota exhaustion affects remote operation, while local `/ais` use remains independent.

The engine remains message-only, with preparation-time expressions, one command per feature and at most 128 active features. No unrestricted agent execution or world-state undo is provided. Owner backups and audit disk monitoring remain necessary.

### Existing loopback and offline modes

`ais pair` still issues a five-minute code and starts a literal IPv4 loopback status-only bridge. Pair on that machine with:

```text
java -jar AetherScript-MCP-0.0.6.jar pair --endpoint http://127.0.0.1:PORT --profile /private/path/status.json
```

Enter the code at the prompt. `serve --profile ...` launches the paired gateway. This credential lasts one hour and cannot enable mutation tools. `ais unpair` closes that listener. For another host, use an authenticated SSH local forward with both bindings on loopback. `connection-details.json` is informational, contains no secret and can become stale after shutdown.

No-argument gateway startup remains offline. MCP uses `2025-06-18`, newline-delimited UTF-8 JSON-RPC over stdio. Tool annotations are descriptive; plugin authorization and source checks enforce server authority.

---

[Security policy](https://github.com/MistDig/AetherScript/blob/v0.0.6/SECURITY.md) · AetherScript 0.0.6
