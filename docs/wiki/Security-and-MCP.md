[Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.5)

---

## Connect the MCP gateway to server status

The gateway has two modes. Offline stdio provides the language template, capability description and source validation. A paired stdio process also reads the running plugin's bounded status snapshot.

Pairing grants the fixed `status` scope. Its live result includes plugin version, readiness, busy state, registry generation, active feature count, recovery state and AI-deployment state. Source authoring and validation continue through the shared local parser.

### Pair on the server console

Run this in the Minecraft server console:

```text
ais pair
```

The plugin prints a loopback endpoint such as `http://127.0.0.1:49152` and a single-use pairing code. The code expires after five minutes. A successful exchange grants a one-hour status session. A new `ais pair` rotates the grant and invalidates the earlier token.

The listener binds to IPv4 loopback on an ephemeral port. It is created by the console command and closes on `ais unpair` or plugin disable. `plugins/AetherScript/connection-details.json` contains the endpoint, transport and scope. This informational file can remain stale after shutdown and is removed at the next startup or unpair. The current console output supplies the endpoint for a fresh pairing. Keep the pairing code private, including console/log access.

### Create the local profile

Run the gateway's pairing command on the same machine, replacing the sample endpoint with the one printed by the plugin:

```text
java -jar AetherScript-MCP-0.0.5.jar pair --endpoint http://127.0.0.1:49152 --profile /private/path/aether-profile.json
```

Enter the code when prompted. The terminal prompt hides input when a Java console is available. With redirected input, the command reads the code from standard input. Keep the code and token out of process arguments and shell history.

The gateway stores the endpoint, scope, token and expiry in an owner-restricted profile. Use an absolute profile path in the MCP client configuration. Treat the profile as a credential and keep it outside your repository, shared folders and server web roots.

### Start the paired MCP process

A stdio client entry uses this executable/argument shape. Replace the JAR and profile paths for your installation:

```json
{
  "command": "java",
  "args": [
    "-jar", "/absolute/path/to/AetherScript-MCP-0.0.5.jar",
    "serve", "--profile", "/private/path/aether-profile.json"
  ]
}
```

### Copyable Codex setup

These Windows examples use `C:/AetherScript/` as the JAR folder and `C:/AetherScript/private/` for the profile. Replace the paths for your installation, and use a Java 25 executable.

```text
codex mcp add aetherscript -- java -jar C:/AetherScript/AetherScript-MCP-0.0.5.jar serve --profile C:/AetherScript/private/aether-profile.json
codex mcp list
```

The equivalent user or project `config.toml` entry is:

```toml
[mcp_servers.aetherscript]
command = "java"
args = ["-jar", "C:/AetherScript/AetherScript-MCP-0.0.5.jar", "serve", "--profile", "C:/AetherScript/private/aether-profile.json"]
```

Use either the CLI registration or the TOML entry. Keep the profile path absolute so the gateway can start from a different working directory. This setup shape was checked against the installed Codex CLI help and official OpenAI documentation. Named-client runtime verification is tracked in the compatibility matrix. [Official OpenAI MCP configuration](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)

The outer configuration format depends on your MCP client. Windows JSON paths need escaped backslashes or forward slashes. Java 25 runs the gateway. The gateway itself needs no model API key.

To use the offline authoring tools, start the same JAR with no arguments:

```text
java -jar AetherScript-MCP-0.0.5.jar
```

| Tool | Behavior |
| --- | --- |
| `aether_server_status` | Offline mode reports offline state. Paired mode requests the authenticated live status snapshot. |
| `aether_capabilities` | Returns the language rules, editable template and local parser capabilities. |
| `aether_validate` | Validates supplied AetherScript, YAML or JSON and returns hashes or source diagnostics. |

The protocol uses MCP `2025-06-18` and newline-delimited JSON-RPC over stdio. The live grant authorizes status reads. Script deployment, server file editing and console execution remain future server-authorized capabilities.

### Revoke or remove a profile

Revoke the grant and close the bridge from the server console:

```text
ais unpair
```

Remove the local credential file:

```text
java -jar AetherScript-MCP-0.0.5.jar forget --profile /private/path/aether-profile.json
```

Server revocation stops access for clients already holding the token. Local removal prevents later processes from loading that profile. Expiry requires a fresh pairing exchange. Server restart/disable ends the in-memory grant.

### Recover a failed setup

| Result | Next step |
| --- | --- |
| Pairing fails or the code expired | Run `ais pair` again in the server console, then use the new code before its five-minute expiry. |
| Profile creation/permission check fails | Choose a private local path and correct its owner access, then issue a fresh pairing code. A successful exchange consumes its code before the profile is saved. |
| Live status reports an expired/revoked session | Pair again, save the new profile and restart the paired stdio process. |
| Remote connection fails | Check that the SSH tunnel is running and that the profile uses the forwarded local endpoint. |

### Use a remote server through a loopback tunnel

For a remote server, keep the plugin listener on its loopback address and create an authenticated SSH local forward. Replace `SERVER_PORT` with the endpoint port printed by `ais pair` on that server:

```text
ssh -N -L 127.0.0.1:8765:127.0.0.1:SERVER_PORT owner@server
```

Keep the tunnel running and pair the gateway against `http://127.0.0.1:8765`. The gateway connects to a local loopback endpoint while SSH authenticates and encrypts the remote leg. Use the same forwarded endpoint when serving the stored profile. Follow your host's SSH access and host-key policy. [OpenSSH local forwarding reference](https://man.openbsd.org/ssh#L)

The gateway accepts literal `http://127.0.0.1:PORT` endpoints and rejects redirects. The bridge validates a literal loopback Host header and rejects browser Origin/Sec-Fetch-Site requests. It bounds workers/queues and pairing request size, allows eight pairing attempts per issued code, and applies a 120-request-per-minute limit.

### Upgrade from 0.0.4

0.0.4 remains a published offline-authoring release. Install the updated plugin and gateway together to use pairing. Existing YAML/JSON and `.aether` definitions keep their feature IDs, saved revisions and normal `/ais` workflow. Back up the plugin data directory, stop Paper and replace the plugin JAR before restarting.

Named MCP clients require their own compatibility checks. The [client matrix](https://github.com/MistDig/AetherScript/blob/main/docs/mcp-clients.md) records that verification separately.

---

[Security policy](https://github.com/MistDig/AetherScript/blob/v0.0.5/SECURITY.md) · [Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.5
