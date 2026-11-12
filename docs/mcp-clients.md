# MCP prototype interoperability

Protocol: MCP 2025-06-18 over stdio, JSON-RPC 2.0, UTF-8 newline framing. Lifecycle and tools are implemented directly for this small prototype; this is not an official SDK integration. Maximum frame: 128 KiB; oversized frames close the process. Maximum script: 64 KiB. Clients launch the MCP over stdio. A paired gateway uses a separate authenticated loopback HTTP connection to Paper for status. See the [pairing guide](wiki/Security-and-MCP.md).

| Client | Version / OS | Discovery / validation | Live status / deploy / reconnect |
|---|---|---|---|
| Codex | Not tested | Not tested | Not tested / unavailable |
| Claude / Claude Code | Not tested | Not tested | Not tested / unavailable |
| Cursor | Not tested | Not tested | Not tested / unavailable |
| Cline | Not tested | Not tested | Not tested / unavailable |
| Automated Java protocol harness | JDK 25, Windows | Tested | Offline and paired status covered by the process harnesses |

Use a client configuration's stdio command/args fields with `java`, `-jar`, and the absolute gateway JAR path. Configuration syntax differs by client/version; no unverified client-specific configuration is shipped.

Reference: https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle and https://modelcontextprotocol.io/specification/2025-06-18/server/tools .
