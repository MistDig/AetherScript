[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.4)

---

## Useful tooling with clear boundaries

The MCP runs offline. No configuration switch enables one. Local recovery commands do not grant the separate MCP process server access.

Run `AetherScript-MCP-0.0.4.jar` separately with Java 25. A stdio server entry uses this executable/argument shape; the outer configuration is client-specific:

```json
{
  "command": "java",
  "args": ["-jar", "/absolute/path/to/AetherScript-MCP-0.0.4.jar"]
}
```

Windows JSON paths need escaped backslashes or forward slashes. The gateway needs no model API key.

| Tool | Actual behavior |
| :--- | :--- |
| `aether_server_status` | Reports offline status, not live server state. |
| `aether_capabilities` | Returns the language rules, editable template and local capabilities. |
| `aether_validate` | Validates caller-provided AetherScript, YAML or JSON; returns hashes or field/line/column diagnostics. |

Protocol: MCP `2025-06-18`, newline-delimited JSON-RPC stdio, initialization, ping and discovery. No file-reading, console or mutation tools. Validation does not imply server authorization.

Universal describes vendor-neutral design. Codex, Claude/Claude Code, Cursor and Cline are **not yet interoperability-certified**; see the [matrix](https://github.com/MistDig/AetherScript/blob/v0.0.4/docs/mcp-clients.md).

Administration checks `aetherscript.admin` again before activation. Invocation checks feature permissions and sender rules. Recovery revalidates saved source. Installed Java plugins and host administrators remain trusted; hashes are not authentication against a privileged editor. Local audit files are not tamper-proof and need owner-managed disk monitoring/rotation.

Before AI deployment: server-authoritative default-deny policy, authenticated identities, principal/server/feature/action scopes, rotation/revocation, quotas, bound approvals, stale-plan checks, transaction recovery and direct bridge-bypass tests. Tool annotations or a model's claimed approval are not authorization.

Remote connectivity additionally needs verified server identity, encrypted authenticated transport, expiry, replay defenses and network-failure tests. No generic shell, server-directory writer or plugin installer is planned as an agent tool.

[Security policy](https://github.com/MistDig/AetherScript/blob/v0.0.4/SECURITY.md)

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.4
