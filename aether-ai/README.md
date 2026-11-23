# Aether AI pilot

Aether AI is an invite-only browser application for AetherScript on Paper. The pilot supports one approved feature per connection and three builds per day across one deployment. A server owner can still connect Codex or Claude directly through the existing AetherScript setup guide.

## Run on a VPS

Use Node.js 24 or newer and keep the repository's `aether-ai`, `docs/assets`, and `site/public` folders together. Run from the repository root.

```sh
node --env-file=/etc/aether-ai.env aether-ai/server.mjs
```

Store the environment file outside the checkout and restrict it to the service account. Configure an HTTPS reverse proxy for the hostname. Pass through the request origin, disable buffering on `/api/build`, and allow up to 300 seconds for a build. Run one app process for this pilot. Back up the SQLite file and protect the session secret separately. Changing that secret invalidates existing saved connections.

| Variable | Use |
| --- | --- |
| `AETHER_AI_URL` | Public HTTPS origin of the app, without a path |
| `AETHER_AI_MODEL_ENDPOINT` | Trusted HTTPS inference endpoint such as the runner's OpenAI-compatible `/v1/chat/completions` URL |
| `AETHER_AI_MODEL_NAME` | Model ID accepted by the inference endpoint |
| `AETHER_AI_MODEL_KEY` | API credential used by the model endpoint, if required |
| `AETHER_AI_SESSION_SECRET` | Random server secret of at least 32 characters |
| `AETHER_AI_INVITES` | Up to five distinct comma-separated private random codes, each at least 20 characters |
| `AETHER_AI_DB` | Persistent SQLite path. Default `data/aether-ai.sqlite` |
| `AETHER_AI_RELAY` | Existing relay origin. Defaults to the project's hosted relay |
| `PORT` | Loopback listener port. Default `8080` |

Use a private HTTPS route or an authenticated proxy to reach the model. The panel token manages the hosting service and belongs outside this app. Plain HTTP is accepted only on the app and upstream loopback address in explicit development mode with `AETHER_AI_LOOPBACK=1`.

## Connect and build

1. On the Paper server, install AetherScript 0.0.30 or newer. Choose a feature ID and enter `ais connect <feature-id>` in the actual server console.
2. In the same browser as Aether AI, open the console link. Select **Other MCP app** in the relay page. Return to Aether AI, enter the same feature ID, and continue to approval. Enable native capabilities for events. Choose an online test player for live command tests.
3. Approve the selected ID. The app confirms the grant against Paper before enabling chat.
4. Describe a welcome message, typed command, or spawn protection rule and click **Build**. The app validates against the server, applies with the last read hash, and tests the active feature. Watch for the revision number, expected output, actual output, and failures.
5. Test event behavior in Minecraft. The app labels join and block results as **event previews**, which do not verify live gameplay or player ranks. Use `ais disconnect` to revoke access on the server.

The model receives the prompt, relevant language guide, and the editable source for the one approved feature. It can propose a script and expected outcomes. The backend chooses the permitted MCP operations and never puts credentials in prompts. If a valid revision activates but a behavioral test fails, that revision can still be active. Use the existing revision commands to inspect or roll back.

## Checks

- `npm test --prefix aether-ai` checks scope, OAuth state and PKCE, the chat backend, the three workflow shapes, and stale or invalid edits.
- With Python Playwright and Edge installed, run `python integration-tests/aether_ai_browser.py` to check keyboard operation, mobile sizing, and reduced-motion animation through a fixture build.
- Set `AETHER_PILOT_SMOKE=1` and run the existing `integration-tests/paper_smoke.py --native-mcp` workflow with disposable Paper dependencies. It uses deterministic model output by default to check the real Paper/relay path.
- To benchmark a model using that fixture, provide `AETHER_AI_MODEL_ENDPOINT` and `AETHER_AI_MODEL_NAME` to the test process. Use only a staging server. Compare output tokens per second including API latency, total build time, and completed tests before changing the hosted model.
