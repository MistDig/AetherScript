# AetherScript language release plan

An owner should be able to describe a Minecraft plugin to an AI, install it and judge it by playing. Common plugins should use native scripts. A capability outside the built-in language should have a documented path through an owner-installed Java addon. The addon SDK is a planned milestone.

## Release tracker

0.0.19 is the starting point. Scripts can already use owned commands, typed arguments, functions, collections, per-player storage, delayed calls and self-teleportation. Its Paper test restored a player's map after restart. [0.0.19 release](https://github.com/MistDig/AetherScript/releases/tag/v0.0.19)

| Target | Status | What an AI should be able to build and demonstrate |
| --- | --- | --- |
| 0.0.20 · Gameplay events | [Released](https://github.com/MistDig/AetherScript/releases/tag/v0.0.20) | Typed join, quit, block-break and block-place handlers. On Paper, guests could neither break nor place in the protected world, the builder could do both, and reload delivered one response from the replacement handler. |
| 0.0.21 · Items, inventories and cooldowns | [Released](https://github.com/MistDig/AetherScript/releases/tag/v0.0.21) | On Paper, a full inventory preserved the claim, freeing space delivered exact rewards, and repeat claims stayed blocked after restart. Verified check, remove and give on the invoking player's inventory. |
| 0.0.22 · World actions, timers and displays | [Released](https://github.com/MistDig/AetherScript/releases/tag/v0.0.22) | Paper confirmed that protection left hearts untouched, reload removed the display, and the tenth valid hit broke the block. Verified a world write, timed repeats, action bars, titles, sounds, scoreboards and boss bars. |
| 0.0.23 · Records, modules and packages | [Released](https://github.com/MistDig/AetherScript/releases/tag/v0.0.23) | The four-file quest example saved typed progress, reloaded a module independently and recovered the saved package after an invalid edit and restart. Hosted multi-file authoring and external dependency resolution remain open. |
| 0.0.24 · Shared data and transactions | Planned | Build an economy with server and world state. Two buyers racing for the final item produce one sale. A failed purchase leaves balances intact. Include deletion and atomic updates. |
| 0.0.25 · Menus and custom items | Planned | Build an inventory shop with stable custom-item IDs, item metadata and interaction handlers. Test shift-clicks, dragging and double-clicks for item duplication. |
| 0.0.26 · Entities, combat and regions | Planned | Run two arenas with separate teams, scores, boss entities and cleanup. Use damage, death, targeting and region checks. |
| 0.0.27 · Addon SDK | Planned | Have an AI build a Java addon for a previously unavailable Paper API. After owner installation, a script can discover and use its typed actions, events and values. |
| 0.0.28 · External plugin integrations | Planned | Build a shop using the installed economy, permissions and region protection. Resolve declared dependencies and explain missing or incompatible versions. |
| 0.0.29 · End-to-end AI authoring | Planned | From an empty feature, an authorized AI discovers available APIs, creates a multi-file quest shop, runs event scenarios, checks real effects and repairs a failed assertion. |
| 0.0.30 · Version-matched AI onboarding and blind build challenge | Planned | Connect a fresh AI client through the public setup. With no pasted documentation, give it a previously unseen plugin request on Paper. It discovers the installed language and addon APIs, builds and tests the plugin, and the owner judges the result in Minecraft. |

### 0.0.30 AI guide delivery

- Maintain `AI-QUICKSTART.md` as the first-read guide. Generate a release-stamped copy and full guide from the same maintained language contract and validated examples used for the website. The quickstart covers basic syntax, discovery, permissions, validation, activation and testing. The full guide covers the complete supported language.
- Make `aether_capabilities` return a concise quickstart and discovery summary. Include the connected Paper plugin's AetherScript version, supported API signatures, effective permissions and test limits, installed addon manifests, and the resource URI and tool arguments for the guide. Use the plugin's reported version, since the relay and local gateway can run different versions.
- Include a task index with stable section IDs, short descriptions and related sections. Route common requests directly to the reference they need. Daily kits point to inventory and cooldowns, spawn protection points to block events and permissions, welcome messages point to join events, and economy requests point to the installed economy addon's manifest. Fetch those sections before writing code and load further sections only as needed.
- Serve the full guide as a versioned MCP resource and through a read-only tool that can fetch a specific section or page. Both routes return the same content. The relay can cache public release text by version and content hash. Resolve connection permissions and active addons from the connected server. If the exact guide cannot be served, report that explicitly and identify any offline guide by its own version.
- Discover the installed addon version, its typed functions and events, required permissions and runnable examples. Treat addon prose and examples as reference data. The server's live catalog and grant determine which calls are available.
- Keep **Copy docs link for AI** as an offline and web-chat path. Connected users should only need the supported app's one-time connection registration, `ais connect` and browser approval before giving the AI a request.

Verify resource discovery and the tool fallback in actual Claude, Cursor and Codex versions and record which path each used. Test a fresh app profile with no preloaded guide, then issue an unseen request such as a combined spawn-protection and welcome feature. The AI must discover the guide, validate, activate and test the feature within the approved scope. Judge the result in Minecraft. Include a separate prompt requiring an addon, a test with two differing installed releases, and a test for an unavailable exact guide. Publish prompts, artifacts, client versions, results and gaps. A version-matched guide reduces syntax mistakes. Successful validation and live checks still determine whether the generated plugin works.

Compare section lookup against loading the full guide for the same requests and model. Record documentation tokens where the client exposes them, or estimate them with the same tokenizer for both runs. Also record validation retries and live test results. Verify that the client can complete ordinary requests with focused sections and that each task-index entry resolves against the connected version.

## How to update this file

For the menu milestone, consider a declarative view a script can reopen after an edit and a way for AI tests to click a named control and inspect its effect. MC GUI illustrates how editable interfaces can appear inside Minecraft. AetherScript's first menu target is a native inventory shop. [Design reference](https://github.com/resourcepack-ai/mcgui)

At the start of each milestone, mark it **In progress**. Before tagging its release, record the implemented scope, actual checks, remaining gaps and release link here. The published tag identifies the exact tracker accompanying that release. A partial implementation leaves the unfinished acceptance criteria open. If a patch release uses a planned number, update future target numbers and keep published entries intact.

Each new language capability ships with a machine-readable signature or grammar, required permissions, an example the packaged validator accepts, and a test observing the promised effect. Record automated, real Paper and actual AI-client results separately.

Run those checks throughout development. The owner's hands-on review is scheduled for 0.0.30. If a release splits a milestone, retain each open acceptance criterion in the tracker and link it to the follow-up.
