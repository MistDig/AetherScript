# MCP client verification

The shared endpoint uses Streamable HTTP and OAuth authorization code with S256 PKCE. Start from the Minecraft console `ais connect`, open the link in the same browser used for your AI app's authorization, then approve exact feature IDs.

| Client | Setup path | Verification |
| --- | --- | --- |
| ChatGPT | Custom hosted MCP connector, subject to account/workspace availability | Actual app untested |
| Claude custom connector | Remote connector settings, subject to account availability | Live OAuth discovery, DCR, and authorization pages for both Claude callbacks checked in 0.0.13. Authenticated tool call pending. |
| Claude Code | Remote HTTP MCP with OAuth | Actual app untested |
| Codex | Hosted MCP via the Windows setup ZIP or native Codex CLI OAuth. | Hosted discovery checked separately from interactive chat. See release evidence. |
| Cursor | Browser install link and OAuth | Actual app untested |
| Gemini Web / Desktop | Hosted OAuth and automatic or per-client manual registration | HTTPS callback, confidential OAuth and CORS tested at protocol level. Actual Gemini app verification pending. |
| Cline | Remote MCP with OAuth where supported by the installed version | Actual app untested |
| Edge browser plus HTTP harness | Real browser consent, hosted requests, Paper restart and revocation | Release evidence records the executed candidate |
| Local gateway | Stdio offline/loopback/personal-relay modes | Automated process regression tests |

The browser page does not verify an app's identity from its self-reported name. Review the registered callback origin. The localhost callback flow is for a desktop client. Hosted callback choices are limited to the displayed provider origins.

The v0.0.6 Codex app-server discovered nine stdio tools with a local relay profile. That historical test did not verify the new hosted browser sign-in.

[Pairing guide](wiki/Security-and-MCP.md)

Version 0.0.8 supports public clients using S256 PKCE and confidential clients using client_secret_basic or client_secret_post with S256 PKCE. It publishes RFC 7591 registration metadata and accepts /register plus the legacy /oauth/register endpoint. See the release evidence for lifecycle and callback tests.
