[Home](https://github.com/MistDig/AetherScript/wiki/Home)

---

## Available tools

Start with `aether_server_status` to confirm which server is connected. A selected-script connection can access only its approved feature IDs. A full-access connection can create and edit any valid native .aether feature ID, including existing scripts.

| Tool | Input | Result and next step |
| --- | --- | --- |
| `aether_server_status` | No arguments | Current live registry generation and server grant. A connection failure is an error. |
| `aether_scripts_list` | No arguments | Selected IDs, or existing native IDs under full access. Full access can create new valid IDs. |
| `aether_scripts_read` | `id` | Editable source, source hash, active hash, revision and generation. Read before editing. |
| `aether_scripts_validate` | `id`, `source` | Validates a complete native script using the server compiler. |
| `aether_scripts_apply` | `id`, `source`, `expectedHash` | Validates, activates, saves and reads back the result. Use the read hash, or `ABSENT` to create. |
| `aether_scripts_test` | `id`, `expectedMessages`, optional `arguments` | Invokes the active feature using a synthetic sender, captures messages and checks exact equality. |

The separate local gateway also exposes `aether_capabilities`, `aether_validate`, and `aether_test`. These can validate and test supplied source offline. Offline success does not imply server authorization.

## A reliable editing workflow

Read the feature and retain its hash. Validate the complete replacement. Apply with that exact hash. Read the feature again and check that the source and active hashes match the intended change. Run a test with the expected messages and report the actual messages and comparison result.

On a stale-hash error, read again before deciding whether to merge the owner's edit. On timeout or network failure, read back first because the operation may have completed. A successful request can be followed by a failure delivering its reply.

## Test limitations

Synthetic tests capture message actions. They do not impersonate a connected player or verify client rendering. A player-only sender rule requires a player test. Test mismatch means the actual messages differ from the expected list, even when invocation completed.

## Boundaries and diagnostics

An unknown ID outside the grant is denied. A malformed or invalid source cannot replace a saved working revision. Use `ais status <id>`, `ais diff <id>`, `ais history <id>` and rollback on the server when investigating a failed update.

The hosted service bounds JSON requests, source size, pending operations and daily work. Keep native sources below the tool schema's source limit. The local compiler supports larger sources, but a transport envelope includes JSON escaping and metadata.

---

[Connect your AI](https://github.com/MistDig/AetherScript/wiki/Security-and-MCP)

For command arguments, pass strings such as `arguments: ["MistDig", "2", "true"]` to the test tool. The compiler applies the declared types and the tool returns actual messages.

In 0.0.8 a superseded, rejected, expired or revoked approval yields HTTP 401 with an invalid_token authentication challenge. The AI client should sign in again with a fresh console link. A fresh valid approval waits briefly for Paper acknowledgement; a longer outage yields a retryable 503 temporarily_unavailable response.
