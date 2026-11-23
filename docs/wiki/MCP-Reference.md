[Home](https://github.com/MistDig/AetherScript/wiki/Home)

---

Fresh 0.0.17 browser approval selects all or selected native feature IDs, an explicit execution capability set and, optionally, one test-player UUID. The AI may create, edit and activate native features within that grant without another per-edit approval. The approved execution set is persisted for those features so installed commands keep running after the AI disconnects. `ais permit` and `ais restrict` remain manual owner overrides; a browser grant cannot override an explicit console ceiling. Legacy personal-relay and older browser grants remain message-only until fresh browser approval; they never expand automatically.

## Available tools

Start with `aether_server_status` to confirm which server is connected. A selected-script connection can access only its approved feature IDs. A full-access connection can create and edit any valid native feature ID, including existing .aether files and packed snapshots. Third-party file management uses a separate grant.

| Tool | Input | Result and next step |
| --- | --- | --- |
| `aether_capabilities` | No arguments | Hosted version, task routes, live addon manifests, approved execution capabilities and test-player boundaries. Offline mode also retains the full local contract. |
| `aether_docs_read` | `section`, optional `version` | Focused guide page for the connected Paper plugin. The offline gateway returns the guide for its own version. |
| `aether_server_status` | No arguments | Current live registry generation and server grant. A connection failure is an error. |
| `aether_scripts_list` | No arguments | Selected IDs, or existing native IDs under full access. Full access can create new valid IDs. |
| `aether_scripts_read` | `id` | Editable source, source hash, active hash, revision and generation. Read before editing. |
| `aether_scripts_validate` | `id`, `source` | Validates a complete native script using the server compiler. |
| `aether_scripts_apply` | `id`, `source`, `expectedHash` | Validates, activates, saves and reads back the result. Use the read hash, or `ABSENT` to create. |
| `aether_scripts_test` | `id`, `expectedMessages`, optional `arguments` | Starts an authorized active-feature test, possibly returning pending and testId; native/player-only tests use the owner-approved real actor and can cause effects. |
| `aether_scripts_test_status` | `id`, `testId` | Polls the same connection's test for actual messages, effects, error and exact comparison. |

The separate local gateway also exposes `aether_validate` and `aether_test`. These can validate and test supplied source offline. Offline success does not imply server authorization.

## Version-matched documentation

Call `aether_capabilities` after checking the server status. The hosted `guide` names the version and section IDs for common tasks. Read the relevant section through `aether_docs_read` and follow `nextSection` when present. The resource URIs `aetherscript://docs/quickstart` and `aetherscript://docs/reference` return the same first page as the tool. The full structured reference is split into pages. Concatenate all `content` values in order before parsing it as JSON. A version mismatch returns an explicit error. Reconnect through `ais connect` on 0.0.30 to add the guide scope to an older grant.

## A reliable editing workflow

Read the feature and retain its hash. Validate the complete replacement. Apply with that exact hash. Read the feature again and check that the source and active hashes match the intended change. Run a test with the expected messages and report the actual messages and comparison result.

On a stale-hash error, read again before deciding whether to merge the owner's edit. On timeout or network failure, read back first because the operation may have completed. A successful request can be followed by a failure delivering its reply.

## Hosted multi-file editing and event previews

With a 0.0.29 Paper plugin and compatible hosted relay, call `aether_scripts_read`, `aether_scripts_validate` and `aether_scripts_apply` with `format: "aetherpack"`. Supply `source` as a JSON string mapping relative `.aether` filenames to their contents. Include one feature entry and every imported module. See the four files in `examples/quests.aetherpack`. Read and keep the current editable hash, use `ABSENT` for creation, and re-read after applying. The active compiled hash and editable hash can differ after an owner edit. Owner-created package directories stay read-only through MCP. Text scripts continue to use the default format.

`aether_events_preview` takes a permitted feature ID plus an `event` with its `kind` and typed `fields`. Use the exact field names and types from the language reference. Results include simulated messages and cancellation intent for the active revision. A preview assumes the supplied snapshot reaches matching handlers. Verify real server permissions, other plugins, cancellation and world effects on Paper. A preview does not dispatch a live event.

Fresh hosted approval can include join, quit, block break, block placement and cancellation. Cancellation permission is separate from permission to receive an event. Existing grants do not gain permissions or tools automatically. Other event and world capabilities still require local owner approval.

## Test limitations

`aether_scripts_test` uses the owner-approved online player for native or player-only commands; the player must hold the command's declared permission. The AI cannot choose or replace that actor. Tests can send messages, write own-player state, wait and teleport the approved player: these are real effects. A start response may return `status: "pending"` and `testId`; poll `aether_scripts_test_status` with `{id,testId}` until completion, then report actual messages, errors, effects and comparison. A pure `sender any` message program without an approved actor retains synthetic compatibility. Offline `aether_test` is always synthetic and host calls fail with `HOST_CONTEXT_REQUIRED`.

Test mismatch means actual messages differ from the expected list. Errors and completed effects remain observable; completed writes or teleports are not rolled back. Job IDs cannot be used across connections or features. Revocation, permission loss and stale active hashes retire pending tests.

## Third-party files with console approval

On the **Paper PC**, use the server console to define the exact target.

| Command | Purpose |
| --- | --- |
| `ais files allow-config gui-settings MCGUI/config.yml yaml click-sound` | Approve one YAML file and the scalar `click-sound` key. Relative paths start under `plugins/`. Use `json` for JSON. |
| `ais files allow-jar rp-engine RPEngine.jar RPEngine` | Allow an exact destination filename and plugin descriptor name. |
| `ais connect --files` | Offer these operations for a fresh connection. Approve the separate third-party checkbox in your browser. |
| `ais files pending` | Inspect each proposal's connection, target, before and after hashes, and selected config diff or JAR metadata. |
| `ais files approve <change-token>` | Confirm one unexpired proposal from an active connection. |
| `ais files deny <change-token>` | Discard that proposal. |
| `ais files rollback <rollback-token>` | Restore the saved bytes if the current hash matches the applied version. |

The app can use `aether_files_list`, `aether_files_config_read {target}` and `aether_files_config_preview {target,expectedHash,replacements}`. Only the approved top-level scalar keys are exposed or editable. A preview does not change the file. Applying a config may reformat the JSON or YAML, including losing comments. Restart or use the third-party plugin's own reload command to read the new values.

For JARs, put a reviewed file in `plugins/AetherScript/file-management/inbox/` on the **Paper PC**. Give the app its filename and SHA-256 from a source you trust. The app calls `aether_files_jar_stage {target,filename,sha256}`. The console displays the plugin name, version, declared dependencies and review hashes. Approval copies the exact artifact to `plugins/`. Restart Paper manually to load it and check the version. Java plugins run with server privileges. AetherScript checks the artifact's identity and digest. Review its source and compatibility before approving. On Windows, a loaded JAR can be locked. If rollback reports `JAR_ROLLBACK_REQUIRES_STOP`, stop Paper and restore the saved bytes from `file-management/<rollback-token>.bak` for a replacement. For a new installation, remove the exact JAR you installed after confirming its SHA-256. The receipt and original backup stay available. Pending approvals expire in 15 minutes. Saved targets and rollback receipts survive restart. Ordinary MCP connections keep their script scopes.

## Boundaries and diagnostics

An unknown ID outside the grant is denied. A malformed or invalid source cannot replace a saved working revision. Use `ais status <id>`, `ais diff <id>`, `ais history <id>` and rollback on the server when investigating a failed update.

The hosted service bounds JSON requests, source size, pending operations and daily work. Keep native sources below the tool schema's source limit. The local compiler supports larger sources, but a transport envelope includes JSON escaping and metadata.

Test jobs retain at most 32 results globally and 8 per connection for up to 180 seconds; execution has a 120-second deadline. Captured message JSON is limited to 12 KiB and receipts to 6 KiB. `outputTruncated` or `receiptsTruncated` signals incomplete capture; truncated messages never count as a successful exact match. These capture limits do not undo effects already performed.

---

[Connect your AI](https://github.com/MistDig/AetherScript/wiki/Security-and-MCP)

For command arguments, pass strings such as `arguments: ["MistDig", "2", "true"]` to the test tool. The compiler applies the declared types and the tool returns actual messages.

In 0.0.8 a superseded, rejected, expired or revoked approval yields HTTP 401 with an invalid_token authentication challenge. The AI client should sign in again with a fresh console link. A fresh valid approval waits briefly for Paper acknowledgement; a longer outage yields a retryable 503 temporarily_unavailable response.
