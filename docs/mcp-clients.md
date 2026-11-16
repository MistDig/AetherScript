# Connect your AI ☁

One hosted MCP URL connects your AI to your Minecraft server:

```text
https://aetherscript-relay.mistdig143.workers.dev/mcp
```

## Minecraft window → browser → AI app

1. On the Minecraft server computer, install the AetherScript plugin and start Paper with Java 25. Keep one plugin JAR installed.
2. In the actual server console, type `ais connect`. Open its private, one-use browser link on the AI computer. Leave that browser open.
3. Choose the AI app. Add the displayed HTTPS MCP URL using the app path below and start OAuth in the same browser.
4. Review the server, callback origin and connection name. Approve all or selected native IDs, execution capabilities and optionally one test-player UUID. The AI cannot choose the player later.
5. Ask the AI to check server status and capabilities. The page shows Connected after Paper answers that app's request.

Hosted setup needs no Java or separate MCP JAR on the AI computer. The computers may use different networks. App availability and organization policy can add steps; there is no universal two-click setup. Plugin-specific steps are implemented in the [pairing page](../relay/src/v2.js) and [Paper connection manager](../plugin-paper/src/main/java/dev/aetherscript/paper/SharedRelayConnector.java).

## Choose your app

| Client | Setup path | Actual-client verification |
| --- | --- | --- |
| Claude web / Desktop | Customize → Connectors → add custom web connector; enter the URL. Under OAuth client choose **Register automatically**, then Connect. Organization owners may need to add the connector first. AetherScript does not implement Claude's published-identity CIMD option. [Claude primary guide](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp) | 0.0.17 actual app untested. Historical 0.0.13 discovery/DCR/authorization-page checks did not complete an authenticated Claude tool call. |
| Cursor | Use Install in Cursor on the pairing page; review its install prompt and complete OAuth. Cursor documents install links; this does not certify an authenticated AetherScript session. [Cursor primary guide](https://prod.cursor.com/docs/mcp/install-links) | Actual app untested. |
| Codex CLI / desktop MCP settings | Use the CLI below, or the installed app's MCP server settings where available. Official desktop instructions specify Settings → MCP servers → Add server → Streamable HTTP, then save/restart and Authenticate. UI labels depend on installed version. [OpenAI primary guide](https://learn.chatgpt.com/docs/extend/mcp?surface=cli) | 0.0.17 interactive app untested. Historical hosted discovery and 0.0.6 stdio discovery remain separate evidence. |
| ChatGPT | Use the account/workspace's supported remote MCP setup. Check the current [OpenAI MCP guide](https://learn.chatgpt.com/docs/extend/mcp); do not assume every account exposes a custom-connector screen. | Actual app untested. |
| Claude Code / Cline | Use the installed client's documented remote HTTP MCP/OAuth setup if available. No verified app-specific click sequence is claimed here. | Actual app untested. |
| Gemini web / desktop | Availability of a remote MCP integration is not certified. If an integration explicitly asks for an OAuth client ID/secret, the pairing page can register its exact displayed callback and authentication method. | Protocol-level HTTPS callback, confidential OAuth and CORS records are historical; actual app untested. |
| Local gateway | Optional stdio offline, loopback-status or personal-relay workflow; separate from hosted setup. | Historical automated process checks; see versioned evidence. |

### Codex CLI

Run on the AI computer:

```sh
codex mcp add aetherscript --url https://aetherscript-relay.mistdig143.workers.dev/mcp
codex mcp login aetherscript
```

If already configured, run login only. The pairing page also offers an optional Windows setup ZIP: extract it and run `Connect-AetherScript.cmd`. This is an AetherScript helper, not a Codex install deeplink. `codex mcp --help`, `codex mcp add --help` and `codex mcp login --help` on the installed CLI confirmed the URL and OAuth commands on 17 November 2026. The [official guide](https://learn.chatgpt.com/docs/extend/mcp?surface=cli) documents OAuth login and `/mcp` connection inspection.

## Approval and testing

Fresh browser approval can authorize native location/state/delay/self-teleport capabilities. Explicit console `ais permit` / `ais restrict` policy remains an owner override. Old browser grants and legacy private-relay grants stay message-only until fresh approval. Granted source applies activate automatically within the approved scope; installed commands retain their persisted capability policy after AI disconnection.

Native/player-only tests need the approved player online with the command's permission. They cause real effects. A pending `aether_scripts_test` response supplies `testId`; poll `aether_scripts_test_status` with `{id,testId}`. Revocation cancels the connection's pending tests; stale revisions cannot keep executing. Pure any-sender message tests without an actor retain synthetic compatibility. Local offline `aether_test` cannot mutate the host.

The callback origin is the identity information to inspect; a self-reported app name proves nothing. Keep console links, connection files and client secrets private. The relay sees source and results. A timeout can follow a completed apply: read source and active hash before retrying.

## Primary sources checked 17 November 2026

- [Claude custom connectors](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp): Customize/Connectors, custom URL, OAuth registration choices and organization setup. Choose Register automatically for this relay.
- [Cursor MCP install links](https://prod.cursor.com/docs/mcp/install-links): browser link, installation prompt and configured server transport.
- [OpenAI MCP configuration](https://learn.chatgpt.com/docs/extend/mcp?surface=cli): desktop MCP configuration, shared configuration, OAuth login and connection inspection; installed CLI help independently confirms `--url` and login. These are configuration instructions, not a claim of live AetherScript app testing.
- [AetherScript hosted page source](../relay/src/v2.js), [Windows helper](../scripts/Connect-AetherScript.cmd) and [operator guide](wiki/Security-and-MCP.md): server-specific pairing, helper and approval behavior. No third-party client source establishes AetherScript's own authorization policy.
