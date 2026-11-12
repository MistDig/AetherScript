# MCP prototype interoperability

Protocol: MCP 2025-06-18 over stdio, JSON-RPC 2.0, UTF-8 newline framing. Lifecycle and tools are implemented directly for this small prototype; this is not an official SDK integration. Maximum frame: 128 KiB; oversized frames close the process. Maximum script: 64 KiB. Clients launch the MCP over stdio. A paired gateway uses either authenticated loopback HTTP for status or an owner-hosted HTTPS relay for scoped `.aether` authoring/tests. See the [pairing guide](wiki/Security-and-MCP.md).

| Client | Version / OS | Discovery / validation | Live status / deploy / reconnect |
|---|---|---|---|
| Codex | Not tested | Not tested | Not tested / not tested |
| Claude / Claude Code | Not tested | Not tested | Not tested / not tested |
| Cursor | Not tested | Not tested | Not tested / not tested |
| Cline | Not tested | Not tested | Not tested / not tested |
| Automated Java protocol harness | JDK 25, Windows | Tested | Offline and paired status covered by the process harnesses |

Use a client configuration's stdio command/args fields with `java`, `-jar`, and the absolute gateway JAR path. Configuration syntax differs by client/version; no unverified client-specific configuration is shipped.

Reference: https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle and https://modelcontextprotocol.io/specification/2025-06-18/server/tools .

On Windows, Codex 0.167.0-alpha.3.2 discovered nine tools from the 0.0.6 gateway using a relay profile. The live-operation tests use a separate stdio harness.
