[Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.7)

---

## Connect your AI

Install AetherScript 0.0.7 in the Minecraft server's plugins folder and start the server. Java 25 is required. Keep one plugin JAR installed.

### On the Minecraft computer

In the actual server console, type `ais connect`. Open the private link it prints on the computer where you use your AI app. The link expires in ten minutes and works once. Keep console logs and the link private.

The plugin creates its own identity automatically. The shared AetherScript service handles browser approval. You can use PCs on different networks.

### On the AI computer

Choose your AI app on the page. Follow its setup instructions and use the same browser for its authorization screen.

For Codex on Windows, download the linked ZIP, extract it, and double-click `Connect-AetherScript.cmd`. It registers the hosted server and opens Codex sign-in. Java and a separate MCP JAR are unnecessary for this hosted connection.

For ChatGPT or Claude, add the displayed hosted MCP address in your app's custom connector settings and begin authorization. Availability depends on your account and workspace settings. For Cursor use the installation link. Claude Code and Cline can use the hosted OAuth address if the installed client supports it. Their actual client verification is recorded in the [matrix](https://github.com/MistDig/AetherScript/blob/v0.0.7/docs/mcp-clients.md).

### Approve and check

On the authorization page, confirm the server name and callback destination. Choose a connection name and exact feature IDs, for example `cloud-note`. Approve the listed operations. An empty list or wildcard is rejected.

Ask your AI to check AetherScript server status. The pairing page shows Connected after Paper answers that client's request. Approval alone shows a waiting state.

Ask the AI to create `cloud-note`, activate it and test its actual output. It can only access the feature IDs you approved. Script tests capture messages from a synthetic sender. Player-only scripts require a player test.

### Manage access

In the server console, `ais connections` lists each connection ID, name and feature grant. Connections survive normal restarts.

Use `ais disconnect <connection-id>` for one app or `ais disconnect` for all apps. Access blocks immediately. Wait for the console's saved-revocation confirmation before restarting.

Connections expire after 30 days. Run `ais connect` again to authorize a fresh connection. Local scripts continue working during a relay outage.

## Privacy and limits

The service uses HTTPS and sees script source and results. Keep `plugins/AetherScript/connections.json` private. It contains the server credential and local grants. Copying it clones the server identity. Each new installation should generate its own file.

The public service bounds enrollment, app registration, connections, daily operations and payload sizes. It runs on a free Cloudflare account, so quota exhaustion can temporarily interrupt remote access. A failed or timed-out apply may have completed. Read the source and active hash before retrying.

Script edits use an expected source hash. A stale or invalid edit is rejected. Revisions and restart recovery remain available. Grants authorize the fixed script operations listed on the page.

## Existing installations

The 0.0.6 personal relay and 0.0.5 loopback status workflows remain available. Their private files are unnecessary for `ais connect`. Use the new browser flow explicitly when migrating.

---

[Security policy](https://github.com/MistDig/AetherScript/blob/v0.0.7/SECURITY.md)
