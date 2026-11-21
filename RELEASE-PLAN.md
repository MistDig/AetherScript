# AetherScript language release plan

An owner should be able to describe a Minecraft plugin to an AI, install it and judge it by playing. Common plugins should use native scripts. A capability outside the built-in language should have a documented path through an owner-installed Java addon. The addon SDK is a planned milestone.

## Release tracker

0.0.19 is the starting point. Scripts can already use owned commands, typed arguments, functions, collections, per-player storage, delayed calls and self-teleportation. Its Paper test restored a player's map after restart. [0.0.19 release](https://github.com/MistDig/AetherScript/releases/tag/v0.0.19)

| Target | Status | What an AI should be able to build and demonstrate |
| --- | --- | --- |
| 0.0.20 · Gameplay events | [Released](https://github.com/MistDig/AetherScript/releases/tag/v0.0.20) | Typed join, quit, block-break and block-place handlers. On Paper, guests could neither break nor place in the protected world, the builder could do both, and reload delivered one response from the replacement handler. |
| 0.0.21 · Items, inventories and cooldowns | Planned | Give a daily kit, detect insufficient inventory space before consuming the claim, and preserve the cooldown after restart. |
| 0.0.22 · World actions, timers and displays | Planned | Give blocks ten hearts with floating text. Count valid hits, respect existing protection, break on the tenth hit and clean up after reload. Expose recurring tasks, action bars, titles, sounds, scoreboards and boss bars. |
| 0.0.23 · Records, modules and packages | Planned | Split quests, player progress and rewards across files in one plugin. Add a second quest independently. Report validation failures with the originating file and line. |
| 0.0.24 · Shared data and transactions | Planned | Build an economy with server and world state. Two buyers racing for the final item produce one sale. A failed purchase leaves balances intact. Include deletion and atomic updates. |
| 0.0.25 · Menus and custom items | Planned | Build an inventory shop with stable custom-item IDs, item metadata and interaction handlers. Test shift-clicks, dragging and double-clicks for item duplication. |
| 0.0.26 · Entities, combat and regions | Planned | Run two arenas with separate teams, scores, boss entities and cleanup. Use damage, death, targeting and region checks. |
| 0.0.27 · Addon SDK | Planned | Have an AI build a Java addon for a previously unavailable Paper API. After owner installation, a script can discover and use its typed actions, events and values. |
| 0.0.28 · External plugin integrations | Planned | Build a shop using the installed economy, permissions and region protection. Resolve declared dependencies and explain missing or incompatible versions. |
| 0.0.29 · End-to-end AI authoring | Planned | From an empty feature, an authorized AI discovers available APIs, creates a multi-file quest shop, runs event scenarios, checks real effects and repairs a failed assertion. |
| 0.0.30 · Blind build challenge | Planned | Use the public setup as a regular owner and give actual AI clients previously unseen plugin requests on Paper, including one requiring a new addon. Judge the plugin in Minecraft against the original prompt. Publish prompts, artifacts, results and remaining gaps. |

## How to update this file

For the menu milestone, consider a declarative view a script can reopen after an edit and a way for AI tests to click a named control and inspect its effect. MC GUI illustrates how editable interfaces can appear inside Minecraft. AetherScript's first menu target is a native inventory shop. [Design reference](https://github.com/resourcepack-ai/mcgui)

At the start of each milestone, mark it **In progress**. Before tagging its release, record the implemented scope, actual checks, remaining gaps and release link here. The published tag identifies the exact tracker accompanying that release. A partial implementation leaves the unfinished acceptance criteria open. If a patch release uses a planned number, update future target numbers and keep published entries intact.

Each new language capability ships with a machine-readable signature or grammar, required permissions, an example the packaged validator accepts, and a test observing the promised effect. Record automated, real Paper and actual AI-client results separately.

Run those checks throughout development. The owner's hands-on review is scheduled for 0.0.30. If a release splits a milestone, retain each open acceptance criterion in the tracker and link it to the follow-up.
